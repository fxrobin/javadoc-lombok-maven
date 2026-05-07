package fr.fxjavadevblog.mvnlmbkjdoc.vehicules;

import lombok.experimental.UtilityClass;

/**
 * Utility class for vehicules.
 * 
 * This class contains utility methods to:
 * <ul>
 *   <li>format a vehicule as a string for pretty printing.</li>
 * </ul>
 * 
 * @version 1.0
 * @since 1.0
 */
@UtilityClass
public class VehiculeUtils {

    private static final String FORMAT_STRING = "Registration number: %s, Brand: %s, Energy: %s";

    /**
     * Format a vehicule as a string for pretty printing.
     * 
     * @param vehicule the vehicule to format
     * @return a formatted string representing the vehicule.
     */
    public String format(Vehicule vehicule) {
        return FORMAT_STRING.formatted(vehicule.getRegistrationNumber(),
            vehicule.getBrand(),
            vehicule.getEnergy());
    }

}
