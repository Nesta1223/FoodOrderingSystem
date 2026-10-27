package com.pbgold.orderpayment.model;

import com.pbgold.orderpayment.model.enums.RestaurantCategory;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    // Manual switch ("pause orders"). Whether the restaurant is open right now also depends on openingHours.
    @Builder.Default
    @Column(name = "is_open", nullable = false)
    private Boolean isOpen = true;

    // Empty schedule means no restriction: the restaurant is open whenever the manual switch is on
    @Builder.Default
    @OneToMany(mappedBy = "restaurant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OpeningHours> openingHours = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public void replaceOpeningHours(List<OpeningHours> newHours) {
        openingHours.clear();
        for (OpeningHours hours : newHours) {
            hours.setRestaurant(this);
            openingHours.add(hours);
        }
    }
}
