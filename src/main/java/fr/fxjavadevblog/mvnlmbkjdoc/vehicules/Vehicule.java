package fr.fxjavadevblog.mvnlmbkjdoc.vehicules;

import java.io.Serializable;
import java.time.LocalDate;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * Vehicule class is a simple class to demonstrate the use of Javadoc.
 * 
 * @version 1.0
 * @since 1.0
 * @see <a href="https://www.fxjavadevblog.fr">FX Java Dev Blog</a>
 */
@Builder
@EqualsAndHashCode(of = "registrationNumber")
@ToString
public class Vehicule implements Serializable {

    /**
     * The registration number of the vehicle.
     * 
     * @return the registration number of the vehicle
     */
    @Getter
    private final String registrationNumber;

    /**
     * The registration date of the vehicle.
     * 
     * @return the registration date of the vehicle
     */
    @Getter
    private final LocalDate registrationDate;

    /**
     * The brand of the vehicle.
     * 
     * Sets the brand of this vehicle.
     */
    @Getter
    private final String brand;

    /**
     * The energy type of the vehicle.
     * 
     * @return the energy type of the vehicle
     */
    @Getter
    private final Energy energy;
}
