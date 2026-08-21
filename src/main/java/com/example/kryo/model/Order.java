package com.example.kryo.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Order implements Serializable {
    private static final long serialVersionUID = 1L;

    private String orderId;
    private Long userId;
    private List<OrderItem> items = new ArrayList<>();
    private String status;
    private BigDecimal totalAmount;
    private LocalDateTime placedAt;

    public Order() {
    }

    public Order(String orderId, Long userId, List<OrderItem> items, String status, BigDecimal totalAmount, LocalDateTime placedAt) {
        this.orderId = orderId;
        this.userId = userId;
        this.items = items != null ? items : new ArrayList<>();
        this.status = status;
        this.totalAmount = totalAmount;
        this.placedAt = placedAt;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public LocalDateTime getPlacedAt() {
        return placedAt;
    }

    public void setPlacedAt(LocalDateTime placedAt) {
        this.placedAt = placedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Order order)) return false;
        return Objects.equals(orderId, order.orderId) &&
                Objects.equals(userId, order.userId) &&
                Objects.equals(items, order.items) &&
                Objects.equals(status, order.status) &&
                Objects.equals(totalAmount, order.totalAmount) &&
                Objects.equals(placedAt, order.placedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId, userId, items, status, totalAmount, placedAt);
    }

    @Override
    public String toString() {
        return "Order{" +
                "orderId='" + orderId + '\'' +
                ", userId=" + userId +
                ", items=" + items +
                ", status='" + status + '\'' +
                ", totalAmount=" + totalAmount +
                ", placedAt=" + placedAt +
                '}';
    }
}
