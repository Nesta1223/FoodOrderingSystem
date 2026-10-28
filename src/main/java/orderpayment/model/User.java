package orderpayment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    /**
     * Unique identifier of the customer, assigned by the database. Orders point to the customer through this
     * id, so it never changes and is never reused for another person.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The customer's name, shown on their orders so the restaurant and delivery know who the order is for.
     * Required, up to 100 characters.
     */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * The customer's email address, used to identify and contact them. It must be unique across all users,
     * so two accounts can never share one address. Required, up to 100 characters.
     */
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    /**
     * Optional phone number for contacting the customer about an order, for example during delivery.
     * Stored as text rather than a number so leading zeros (0812345678) and prefixes (+66) are kept.
     * Up to 20 characters.
     */
    @Column(length = 20)
    private String phone;

    /**
     * When the customer account was created. Filled in automatically when the row is first saved and never
     * changed afterwards, so it is a reliable sign-up date.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
