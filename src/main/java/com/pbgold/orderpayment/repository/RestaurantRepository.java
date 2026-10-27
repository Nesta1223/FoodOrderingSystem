package com.pbgold.orderpayment.repository;

import com.pbgold.orderpayment.model.Restaurant;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    // Fetch the schedule in the same query; the list view needs it for every restaurant
    @EntityGraph(attributePaths = "openingHours")
    List<Restaurant> findByIsOpenTrueOrderByNameAsc();
}
