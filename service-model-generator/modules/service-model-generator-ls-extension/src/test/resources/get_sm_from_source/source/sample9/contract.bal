// AUTO-GENERATED FILE.
// This file is auto-generated by the Ballerina OpenAPI tool.

import ballerina/http;

@http:ServiceConfig {basePath: "/v1"}
type PizzaShop service object {
    *http:ServiceContract;
    resource function get pizzas() returns Pizza[];
    resource function get orders(string? customerId) returns Order[];
    resource function post orders(@http:Payload OrderRequest payload) returns Order|http:BadRequest;
    resource function get orders/[string orderId]() returns Order|http:NotFound;
    resource function patch orders/[string orderId](@http:Payload OrderUpdate payload) returns http:Ok|http:BadRequest;
};

public type Pizza record {
    string? id?;
    string? name?;
    string? description?;
    decimal? basePrice?;
    string[]? toppings?;
};

public type Order record {
    string? id?;
    string? customerId?;
    "PENDING"|"PREPARING"|"OUT_FOR_DELIVERY"|"DELIVERED"|"CANCELLED" status?;
    decimal? totalPrice?;
    Order_pizzas[]? pizzas?;
};

public type OrderUpdate record {
    "PREPARING"|"OUT_FOR_DELIVERY"|"DELIVERED"|"CANCELLED" status?;
};

public type Order_pizzas record {
    string? pizzaId?;
    int? quantity?;
};

public type OrderRequest record {
    string? customerId;
    OrderRequest_pizzas[]? pizzas;
};

public type OrderRequest_pizzas record {
    string? pizzaId?;
    int? quantity?;
    string[]? customizations?;
};
