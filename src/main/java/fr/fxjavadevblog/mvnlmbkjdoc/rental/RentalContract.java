package fr.fxjavadevblog.mvnlmbkjdoc.rental;

import java.time.LocalDate;

import org.jilt.Builder;
import org.jilt.BuilderStyle;
import org.jilt.Opt;

import fr.fxjavadevblog.mvnlmbkjdoc.vehicules.Vehicule;

/**
 * Represents a vehicle rental contract between a driver and the garage.
 *
 * <p>This class demonstrates JILT's <strong>Staged Builder</strong> pattern:
 * each required field is enforced at compile-time via a dedicated interface,
 * making it impossible to call {@code build()} before all mandatory fields
 * are set.
 *
 * <p>The generated builder enforces this exact field order:
 * <ol>
 *   <li>{@code driver} — the renting driver</li>
 *   <li>{@code vehicule} — the rented vehicle</li>
 *   <li>{@code startDate} — contract start</li>
 *   <li>{@code endDate} — contract end</li>
 * </ol>
 * Optional fields ({@code notes}) may be set or skipped.
 *
 * <p>Example usage — the compiler rejects any attempt to call {@code build()}
 * before all required setters are invoked:
 * <pre>{@code
 * RentalContract contract = RentalContractBuilder.rentalContract()
 *     .driver(driver)
 *     .vehicule(vehicule)
 *     .startDate(LocalDate.of(2026, 6, 1))
 *     .endDate(LocalDate.of(2026, 6, 15))
 *     .notes("No pets allowed")   // optional — can be omitted
 *     .build();
 * }</pre>
 *
 * @apiNote
 * The staged builder pattern shines for domain objects where field order is
 * meaningful and partial construction must be forbidden. Compare with Lombok's
 * {@code @Builder} which imposes no order and silently defaults missing fields to
 * {@code null} or {@code 0}.
 *
 * @since 1.0
 * @see Driver
 * @see Vehicule
 */
@Builder(style = BuilderStyle.STAGED)
public final class RentalContract {

    /**
     * The driver who signed this contract. Never {@code null}.
     */
    private final Driver driver;

    /**
     * The vehicle covered by this contract. Never {@code null}.
     */
    private final Vehicule vehicule;

    /**
     * The date on which the rental period begins. Never {@code null}.
     */
    private final LocalDate startDate;

    /**
     * The date on which the rental period ends. Must not precede the start date.
     */
    private final LocalDate endDate;

    /**
     * Free-text notes attached to this contract (e.g. damage remarks, special conditions).
     * May be {@code null} if no notes were provided.
     */
    @Opt
    private final String notes;

    RentalContract(Driver driver, Vehicule vehicule, LocalDate startDate, LocalDate endDate, String notes) {
        this.driver = driver;
        this.vehicule = vehicule;
        this.startDate = startDate;
        this.endDate = endDate;
        this.notes = notes;
    }

    /**
     * Returns the driver who signed this contract.
     *
     * @return the driver; never {@code null}
     */
    public Driver getDriver() { return driver; }

    /**
     * Returns the vehicle covered by this contract.
     *
     * @return the vehicle; never {@code null}
     */
    public Vehicule getVehicule() { return vehicule; }

    /**
     * Returns the rental start date.
     *
     * @return start date; never {@code null}
     */
    public LocalDate getStartDate() { return startDate; }

    /**
     * Returns the rental end date.
     *
     * @return end date; never {@code null}
     */
    public LocalDate getEndDate() { return endDate; }

    /**
     * Returns free-text notes attached to this contract, if any.
     *
     * @return notes string, or {@code null} if none were provided
     */
    public String getNotes() { return notes; }

    @Override
    public String toString() {
        return "RentalContract{driver=" + driver + ", vehicule=" + vehicule
                + ", startDate=" + startDate + ", endDate=" + endDate
                + ", notes='" + notes + "'}";
    }
}
