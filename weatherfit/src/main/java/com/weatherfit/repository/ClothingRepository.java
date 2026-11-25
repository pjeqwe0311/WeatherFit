
package com.weatherfit.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.weatherfit.entity.Clothing;

public interface ClothingRepository extends JpaRepository<Clothing, Long> {

}
