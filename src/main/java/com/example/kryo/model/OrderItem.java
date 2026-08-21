package com.example.kryo.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

public class OrderItem implements Serializable {
    private static final long serialVersionUID = 1L;

    private String productId;
    private String productName;
    private int quantity;
    private BigDecimal unitPrice;

    public OrderItem() {
    }

    public OrderItem(String productId, String productName, int quantity, BigDecimal unitPrice) {
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrderItem that)) return false;
        return quantity == that.quantity &&
                Objects.equals(productId, that.productId) &&
                Objects.equals(productName, that.productName) &&
                Objects.equals(unitPrice, that.unitPrice);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, productName, quantity, unitPrice);
    }

    @Override
    public String toString() {
        return "OrderItem{" +
                "productId='" + productId + '\'' +
                ", productName='" + productName + '\'' +
                ", quantity=" + quantity +
                ", unitPrice=" + unitPrice +
                '}';
    }
}
