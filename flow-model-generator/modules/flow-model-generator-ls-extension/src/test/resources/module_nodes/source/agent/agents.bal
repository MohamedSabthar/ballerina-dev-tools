import ballerina/io;
import ballerina/lang.regexp;
import ballerinax/ai;

configurable string apiKey = ?;
configurable string deploymentId = ?;
configurable string apiVersion = ?;
configurable string serviceUrl = ?;

final ai:ModelProvider model = check new ai:AzureOpenAiProvider({auth: {apiKey}}, serviceUrl, deploymentId, apiVersion);
final ai:Agent agent = check new (
    systemPrompt = {
        role: "Telegram Assistant",
        instructions: "Assist the users with their requests, whether it's for information, " +
            "tasks, or troubleshooting. Provide clear, helpful responses in a friendly and professional manner."
    },
    model = model,
    tools = [sum, multiply],
    memory = new ai:MessageWindowChatMemory(10) // Available by default
);
