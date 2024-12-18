/*
 *  Copyright (c) 2024, WSO2 LLC. (http://www.wso2.com)
 *
 *  WSO2 LLC. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package io.ballerina.flowmodelgenerator.core;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import io.ballerina.compiler.api.ModuleID;
import io.ballerina.compiler.api.SemanticModel;
import io.ballerina.compiler.api.symbols.ArrayTypeSymbol;
import io.ballerina.compiler.api.symbols.RecordFieldSymbol;
import io.ballerina.compiler.api.symbols.RecordTypeSymbol;
import io.ballerina.compiler.api.symbols.Symbol;
import io.ballerina.compiler.api.symbols.SymbolKind;
import io.ballerina.compiler.api.symbols.TypeDefinitionSymbol;
import io.ballerina.compiler.api.symbols.TypeDescKind;
import io.ballerina.compiler.api.symbols.TypeSymbol;
import io.ballerina.compiler.api.symbols.UnionTypeSymbol;
import io.ballerina.flowmodelgenerator.core.model.Member;
import io.ballerina.flowmodelgenerator.core.model.ModuleInfo;
import io.ballerina.flowmodelgenerator.core.model.NodeKind;
import io.ballerina.flowmodelgenerator.core.model.TypeData;
import io.ballerina.flowmodelgenerator.core.utils.CommonUtils;
import io.ballerina.flowmodelgenerator.core.utils.TypeUtils;
import io.ballerina.projects.Document;
import io.ballerina.projects.Module;
import io.ballerina.tools.text.LinePosition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Manage creation, retrieving and updating operations related to types.
 *
 * @since 2.0.0
 */
public class TypesManager {
    private static final Gson gson = new Gson();
    private final Module module;

    private static final Predicate<Symbol> supportedTypesPredicate = symbol -> {
        if (symbol.getName().isEmpty()) {
            return false;
        }

        if (symbol.kind() == SymbolKind.ENUM) {
            return true;
        }

        if (symbol.kind() != SymbolKind.TYPE_DEFINITION) {
            return false;
        }

        return switch (((TypeDefinitionSymbol) symbol).typeDescriptor().typeKind()) {
            case RECORD, ARRAY, UNION, ERROR -> true;
            default -> false;
        };
    };

    public TypesManager(Module module) {
        this.module = module;
    }

    public JsonElement getAllTypes() {
        SemanticModel semanticModel = this.module.getCompilation().getSemanticModel();
        Map<String, Symbol> symbolMap = semanticModel.moduleSymbols().stream()
                .filter(supportedTypesPredicate)
                .collect(Collectors.toMap(symbol -> symbol.getName().get(), symbol -> symbol));

        // Now we have all the defined types in the module scope
        // Now we need to get foreign types that we have defined members of the types
        // e.g: ballerina\time:UTC in Person record as a type of field `dateOfBirth`
        new HashMap<>(symbolMap).forEach((key, element) -> {
            if (element.kind() != SymbolKind.TYPE_DEFINITION) {
                return;
            }
            TypeSymbol typeSymbol = ((TypeDefinitionSymbol) element).typeDescriptor();
            addMemberTypes(typeSymbol, symbolMap);
        });

        List<TypeData> allTypes = new ArrayList<>();
        symbolMap.values().forEach(symbol -> {
            if (symbol.kind() == SymbolKind.TYPE_DEFINITION) {
                TypeDefinitionSymbol typeDef = (TypeDefinitionSymbol) symbol;
                if (typeDef.typeDescriptor().typeKind() == TypeDescKind.RECORD) {
                    allTypes.add(getRecordType(typeDef));
                }
            }
        });

        return gson.toJsonTree(allTypes);
    }

    public JsonElement getType(Document document, LinePosition linePosition) {
        SemanticModel semanticModel = this.module.getCompilation().getSemanticModel();
        Optional<Symbol> symbol = semanticModel.symbol(document, linePosition);
        if (symbol.isEmpty() || symbol.get().kind() != SymbolKind.TYPE_DEFINITION) {
            return null;
        }

        TypeDefinitionSymbol typeDef = (TypeDefinitionSymbol) symbol.get();
        // Only supports records so far. TODO: support unions, array, errors
        if (typeDef.typeDescriptor().typeKind() == TypeDescKind.RECORD) {
            return gson.toJsonTree(getRecordType(typeDef));
        }

        return null;
    }

    private void addMemberTypes(TypeSymbol typeSymbol, Map<String, Symbol> symbolMap) {
        // Record
        switch (typeSymbol.typeKind()) {
            case RECORD -> {
                RecordTypeSymbol recordTypeSymbol = (RecordTypeSymbol) typeSymbol;

                // Type inclusions
                List<TypeSymbol> inclusions = recordTypeSymbol.typeInclusions();
                inclusions.forEach(inc -> {
                    addToMapIfForeignAndNotAdded(symbolMap, inc);
                });

                // Rest field
                Optional<TypeSymbol> restTypeDescriptor = recordTypeSymbol.restTypeDescriptor();
                if (restTypeDescriptor.isPresent()) {
                    TypeSymbol restType = restTypeDescriptor.get();
                    addToMapIfForeignAndNotAdded(symbolMap, restType);
                }

                // Field members
                Map<String, RecordFieldSymbol> fieldSymbolMap = recordTypeSymbol.fieldDescriptors();
                fieldSymbolMap.forEach((key, field) -> {
                    TypeSymbol ts = field.typeDescriptor();
                    if (ts.typeKind() == TypeDescKind.ARRAY || ts.typeKind() == TypeDescKind.UNION) {
                        addMemberTypes(ts, symbolMap);
                    } else {
                        addToMapIfForeignAndNotAdded(symbolMap, ts);
                    }
                });
            }
            case UNION -> {
                UnionTypeSymbol unionTypeSymbol = (UnionTypeSymbol) typeSymbol;
                List<TypeSymbol> unionMembers = unionTypeSymbol.memberTypeDescriptors();
                unionMembers.forEach(member -> {
                    if (member.typeKind() == TypeDescKind.ARRAY) {
                        addMemberTypes(member, symbolMap);
                    } else {
                        addToMapIfForeignAndNotAdded(symbolMap, member);
                    }
                });
            }
            case ARRAY -> {
                ArrayTypeSymbol arrayTypeSymbol = (ArrayTypeSymbol) typeSymbol;
                TypeSymbol arrMemberTypeDesc = arrayTypeSymbol.memberTypeDescriptor();
                if (arrMemberTypeDesc.typeKind() == TypeDescKind.ARRAY
                        || arrMemberTypeDesc.typeKind() == TypeDescKind.UNION) {
                    addMemberTypes(arrMemberTypeDesc, symbolMap);
                } else {
                    addToMapIfForeignAndNotAdded(symbolMap, arrMemberTypeDesc);
                }
            }
            default -> {
            }
        }
    }

    private void addToMapIfForeignAndNotAdded(Map<String, Symbol> foreignSymbols, TypeSymbol type) {
        if (type.typeKind() != TypeDescKind.TYPE_REFERENCE
                || type.getName().isEmpty()
                || type.getModule().isEmpty()) {
            return;
        }

        String name = type.getName().get();

        if (CommonUtils.isWithinPackage(type, ModuleInfo.from(this.module.descriptor()))) {
            return;
        }

        String typeName = TypeUtils.generateReferencedTypeId(type, ModuleInfo.from(this.module.descriptor()));
        if (!foreignSymbols.containsKey(name)) {
            foreignSymbols.put(typeName, type);
        }
    }

    private TypeData getRecordType(TypeDefinitionSymbol recTypeDefSymbol) {
        TypeData.TypeDataBuilder typeDataBuilder = new TypeData.TypeDataBuilder();
        ModuleInfo currentModuleInfo = ModuleInfo.from(this.module.descriptor());

        String typeName;
        if (CommonUtils.isWithinPackage(recTypeDefSymbol, currentModuleInfo)) {
            typeName = recTypeDefSymbol.getName().get();
        } else {
            ModuleID recTypeModuleId = recTypeDefSymbol.getModule().get().id();
            typeName = String.format("%s/%s:%s",
                    recTypeModuleId.orgName(), recTypeModuleId.packageName(), recTypeDefSymbol.getName().get());
        }

        // metadata and codedata
        typeDataBuilder
                .name(typeName)
                .editable()
                .metadata()
                    .label(typeName)
                    .description(recTypeDefSymbol.documentation().isEmpty() ? "" :
                            recTypeDefSymbol.documentation().get().description().orElse(""))
                    .stepOut()
                .codedata()
                    .lineRange(recTypeDefSymbol.getLocation().get().lineRange())
                    .node(NodeKind.RECORD);

        // properties
        typeDataBuilder.properties()
                .name(typeName, false, true, false)
                .description(
                        recTypeDefSymbol.documentation().isEmpty() ? ""
                                : recTypeDefSymbol.documentation().get().description().orElse(""),
                        false, true, false)
                .isArray("false", true, true, true)
                .arraySize("", true, true, true);

        RecordTypeSymbol typeDesc = (RecordTypeSymbol) recTypeDefSymbol.typeDescriptor();

        // includes
        List<String> inclusions = new ArrayList<>();
        typeDesc.typeInclusions().forEach(inc -> {
            if (inc.typeKind() == TypeDescKind.TYPE_REFERENCE) {
                inclusions.add(TypeUtils.generateReferencedTypeId(inc, currentModuleInfo));
            }
        });
        typeDataBuilder.includes(inclusions);

        Member.MemberBuilder memberBuilder = new Member.MemberBuilder();

        // rest member
        Optional<TypeSymbol> restTypeDesc = typeDesc.restTypeDescriptor();
        if (restTypeDesc.isPresent()) {
            TypeSymbol restTypeSymbol = restTypeDesc.get();
            typeDataBuilder.restMember(memberBuilder
                    .kind(Member.MemberKind.FIELD)
                    .type(CommonUtils.getTypeSignature(restTypeSymbol, currentModuleInfo))
                    .refs(TypeUtils.getTypeRefIds(restTypeSymbol, currentModuleInfo))
                    .build());
        }

        // members
        Map<String, Member> fieldMembers = new HashMap<>();
        typeDesc.fieldDescriptors().forEach((name, field) -> {
            TypeSymbol fieldTypeDesc = field.typeDescriptor();
            fieldMembers.put(name,
                    memberBuilder
                            .kind(Member.MemberKind.FIELD)
                            .type(CommonUtils.getTypeSignature(fieldTypeDesc, currentModuleInfo))
                            .refs(TypeUtils.getTypeRefIds(fieldTypeDesc, currentModuleInfo))
                            .name(name)
                            .docs(field.documentation().isEmpty() ? ""
                                    : field.documentation().get().description().orElse(""))
                            // TODO: Add default value
                            .build());
        });
        typeDataBuilder.members(fieldMembers);

        // TODO: Add support for annotations

        return typeDataBuilder.build();
    }
}
