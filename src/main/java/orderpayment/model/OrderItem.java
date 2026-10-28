package orderpayment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    /**
     * Unique identifier of this order line, assigned by the database.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The order this line belongs to. Required. An order has many lines, one per dish ordered.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /**
     * The dish that was ordered. Required. It is used to show the dish name on the order. The price of this
     * line does not come from the dish but from priceAtOrder, because the dish's price can change later.
     * Many order lines can point to the same dish, since many customers order the same thing.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    /**
     * How many portions of the dish were ordered. At least 1 (validated when the order is created).
     */
    @Column(nullable = false)
    private Integer quantity;

    /**
     * The price of ONE portion at the moment the order was placed: a copy of MenuItem.price, not a link to it.
     * It cannot be updated afterwards (updatable = false). This guarantees the price the customer saw is the
     * price they pay, and that old orders, totals and refunds stay correct even after the restaurant changes
     * its menu prices. The line subtotal is quantity x this value. Stored as DECIMAL(10,2).
     */
    @Column(name = "price_at_order", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal priceAtOrder;

    public BigDecimal getSubtotal() {
        return priceAtOrder.multiply(BigDecimal.valueOf(quantity));
    }
}
