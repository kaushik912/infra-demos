package com.demo.newrelic.widget;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WidgetRepository extends JpaRepository<Widget, Long> {

    List<Widget> findByCategory(String category);

    // Deliberately unindexed LIKE scan so the query shows up as a distinct,
    // non-trivial DB span in New Relic's transaction trace / database view.
    @Query("select w from Widget w where w.name like %:fragment%")
    List<Widget> searchByNameFragment(String fragment);
}
