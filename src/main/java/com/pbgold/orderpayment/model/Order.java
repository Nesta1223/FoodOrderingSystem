package com.pbgold.orderpayment.model;

import com.pbgold.orderpayment.model.enums.OrderStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// "order" is a reserved word in SQL, so the table is named "orders"
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    /**
     * Unique identifier of the order, assigned by the database. Order lines and payments point to the order
     * through this id, and customers use it to look an order up.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The customer who placed the order. Required. This is what makes the order show up in that customer's
     * order history.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * The restaurant the order was placed at and will be prepared by. Required. Every dish in the order
     * belongs to this restaurant, and the order is only accepted while the restaurant is open.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    /**
     * Where the order is in its life: PENDING, then CONFIRMED, PREPARING and DELIVERED, or CANCELLED.
     * It only moves forward. A new order starts as PENDING, and only a PENDING order can be cancelled.
     * Required.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    /**
     * The amount the customer has to pay: the sum of quantity x locked price over all order lines.
     * It is always calculated by the server when the order is created and never accepted from the client,
     * so a customer cannot lower what they pay. A payment is only accepted if it equals this amount exactly.
     * Stored as DECIMAL(10,2).
     */
    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    /**
     * The lines of the order: which dish, how many, and at what locked price. This is not a column on the
     * orders table; it is the other side of OrderItem.order. Saving the order saves its lines, and a line
     * removed from this list is deleted.
     */
    @Builder.Default
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    /**
     * When the customer placed the order. Filled in automatically on first save and never changed.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * When the order last changed, for example when its status moved or it was cancelled. Refreshed
     * automatically on every update, so it shows how long an order has been in its current status.
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }
}
