package com.pbgold.orderpayment.model;

import com.pbgold.orderpayment.model.enums.OpeningHours;
import com.pbgold.orderpayment.model.enums.RestaurantCategory;
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RestaurantCategory category;

    // Daily schedule preset, applied to every day of the week
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "opening_hours", nullable = false, length = 20, columnDefinition = "varchar(20) NOT NULL DEFAULT 'ALL_DAY'")
    private OpeningHours openingHours = OpeningHours.ALL_DAY;

    // Temporary manual pause. Orders are blocked while now < pausedUntil; after that the restaurant
    // follows its opening hours again with no cleanup needed, so a forgotten pause cannot last forever.
    // Stored in the application time zone (app.timezone), like the opening hours.
    @Column(name = "paused_until")
    private LocalDateTime pausedUntil;

    @Column(name = "pause_reason", length = 200)
    private String pauseReason;

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
