package fr.fxjavadevblog.mvnlmbkjdoc.rental;

import org.jilt.Builder;
import org.jilt.BuilderStyle;

/**
 * Represents a licensed vehicle driver eligible for rental services.
 *
 * <p>Instances are created via the JILT-generated {@code DriverBuilder}.
 * Unlike Lombok's {@code @Builder}, JILT generates a fully typed builder
 * as a separate, inspectable {@code .java} source file.
 *
 * <p>Example usage:
 * <pre>{@code
 * Driver driver = DriverBuilder.driver()
 *     .firstName("Jean")
 *     .lastName("Dupont")
 *     .licenseNumber("AB-123456")
 *     .age(35)
 *     .build();
 * }</pre>
 *
 * @since 1.0
 * @see RentalContract
 */
@Builder(style = BuilderStyle.CLASSIC)
public final class Driver {

    /**
     * Driver's given name. Never {@code null}.
     */
    private final String firstName;

    /**
     * Driver's family name. Never {@code null}.
     */
    private final String lastName;

    /**
     * National driving licence number. Must be unique per person.
     * Format example: {@code "AB-123456"}.
     */
    private final String licenseNumber;

    /**
     * Age of the driver in years. Must be &ge; 18.
     */
    private final int age;

    Driver(String firstName, String lastName, String licenseNumber, int age) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.licenseNumber = licenseNumber;
        this.age = age;
    }

    /**
     * Returns the driver's given name.
     *
     * @return first name; never {@code null}
     */
    public String getFirstName() { return firstName; }

    /**
     * Returns the driver's family name.
     *
     * @return last name; never {@code null}
     */
    public String getLastName() { return lastName; }

    /**
     * Returns the national driving licence number.
     *
     * @return licence number; never {@code null}
     */
    public String getLicenseNumber() { return licenseNumber; }

    /**
     * Returns the driver's age in years.
     *
     * @return age &ge; 18
     */
    public int getAge() { return age; }

    @Override
    public String toString() {
        return "Driver{firstName='" + firstName + "', lastName='" + lastName
                + "', licenseNumber='" + licenseNumber + "', age=" + age + '}';
    }
}
