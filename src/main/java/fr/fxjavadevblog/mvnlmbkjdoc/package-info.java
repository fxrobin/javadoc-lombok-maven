/**
 * Root package for the Maven + Lombok + Javadoc demonstration project.
 *
 * <p>This project demonstrates how to generate complete Javadoc for Lombok-annotated
 * Java classes using the Maven Javadoc plugin with the delombok pre-processing step.</p>
 *
 * <p>Sub-packages:</p>
 * <ul>
 *   <li>{@link fr.fxjavadevblog.mvnlmbkjdoc.vehicules} — vehicle domain model and utilities</li>
 *   <li>{@link fr.fxjavadevblog.mvnlmbkjdoc.garage} — garage collection model</li>
 * </ul>
 *
 * <p>Generate Javadoc with Lombok support:</p>
 * <pre>{@code
 * mvn clean compile javadoc:javadoc -Pjavadoc
 * }</pre>
 *
 * @since 1.0
 */
package fr.fxjavadevblog.mvnlmbkjdoc;
