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
@Table(name = "menu_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuItem {

    /**
     * Unique identifier of the menu item, assigned by the database. Order lines point to the dish through
     * this id.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The restaurant this dish belongs to. A dish belongs to exactly one restaurant, and an order may only
     * contain dishes from the restaurant it is placed at. Required. Loaded lazily, so reading a menu item
     * does not fetch the restaurant unless it is needed.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    /**
     * The dish name customers see on the menu and on their order. Required, up to 100 characters.
     */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * The dish's CURRENT price. It is a live value that the restaurant can change over time. An order does
     * not point at it: the price is copied into OrderItem.priceAtOrder when the order is placed, so a later
     * price change never alters orders that already exist. Money is stored as BigDecimal with 2 decimals
     * (DECIMAL(10,2)), never as double, to avoid rounding errors.
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /**
     * Whether the dish can be ordered right now. False means unavailable (for example sold out), and ordering
     * it is rejected. A flag is used instead of deleting the row, so past orders keep pointing to the dish
     * and its name. New dishes start as available.
     */
    @Builder.Default
    @Column(name = "is_available", nullable = false)
    private Boolean isAvailable = true;
}
