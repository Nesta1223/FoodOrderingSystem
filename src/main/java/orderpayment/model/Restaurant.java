package orderpayment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import orderpayment.model.enums.OpeningHours;
import orderpayment.model.enums.RestaurantCategory;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "restaurants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Restaurant {

    /**
     * Unique identifier of the restaurant, assigned by the database. Menu items and orders point to the
     * restaurant through this id.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The name customers see when browsing restaurants and on their orders. Required, up to 100 characters.
     */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * The kind of restaurant (FOOD, DRINK or DESSERT), so customers can browse by type. Required.
     * Stored as readable text, so reordering the enum can never change the meaning of existing rows.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RestaurantCategory category;

    /**
     * The restaurant's opening hours: one preset (for example OFFICE_HOURS 09:00-21:00) that applies to
     * every day of the week. The default ALL_DAY means no time restriction. Together with pausedUntil it
     * decides whether the restaurant can take orders right now. "Open" is never stored: it is calculated
     * from this value and the current time on every request, so it cannot become out of date.
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "opening_hours", nullable = false, length = 20, columnDefinition = "varchar(20) NOT NULL DEFAULT 'ALL_DAY'")
    private OpeningHours openingHours = OpeningHours.ALL_DAY;

    /**
     * Until when the owner has paused orders (for example because the kitchen is overloaded). While the
     * current time is before this moment the restaurant counts as closed, even inside its opening hours.
     * Null means not paused. It is a timestamp rather than an on/off flag on purpose: once it passes, the
     * restaurant follows its opening hours again by itself, so a forgotten pause can never close a
     * restaurant forever. A pause can only close a restaurant, never open one outside its opening hours.
     * Stored in the application time zone (app.timezone), like the opening hours.
     */
    @Column(name = "paused_until")
    private LocalDateTime pausedUntil;

    /**
     * Optional note explaining why orders are paused (for example "Kitchen overloaded"). Only meaningful
     * while pausedUntil is in the future, and cleared when the restaurant is resumed. Up to 200 characters.
     */
    @Column(name = "pause_reason", length = 200)
    private String pauseReason;

    /**
     * When the restaurant was registered. Filled in automatically on first save and never changed.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public void pause(LocalDateTime until, String reason) {
        this.pausedUntil = until;
        this.pauseReason = reason;
    }

    public void resume() {
        this.pausedUntil = null;
        this.pauseReason = null;
    }

    public boolean isPausedAt(LocalDateTime moment) {
        return pausedUntil != null && moment.isBefore(pausedUntil);
    }
}
