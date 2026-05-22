package fr.fxjavadevblog.mvnlmbkjdoc.rental;

import org.jilt.Builder;
import org.jilt.BuilderStyle;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * Represents a licensed vehicle driver eligible for rental services.
 *
 * <p>Instances are created via the JILT-generated {@code DriverBuilder}.
 * This class combines JILT's {@code @Builder} with Lombok's accessors and
 * utility annotations, demonstrating that both processors coexist on the same class.
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
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
@ToString
@EqualsAndHashCode(of = "licenseNumber")
public final class Driver {

    /**
     * Driver's given name. Never {@code null}.
     *
     * @return first name; never {@code null}
     */
    private final String firstName;

    /**
     * Driver's family name. Never {@code null}.
     *
     * @return last name; never {@code null}
     */
    private final String lastName;

    /**
     * National driving licence number. Must be unique per person.
     * Format example: {@code "AB-123456"}.
     *
     * @return licence number; never {@code null}
     */
    private final String licenseNumber;

    /**
     * Age of the driver in years. Must be &ge; 18.
     *
     * @return age &ge; 18
     */
    private final int age;
}
