# Maven Javadoc Lombok Java 21 Demo

Projet de démonstration pour la génération de Javadoc avec Lombok et Java 21.

Ce projet illustre comment générer une Javadoc complète prenant en compte les annotations Lombok (@Getter, @Setter, @Builder, @UtilityClass, etc.) en utilisant le plugin delombok.

## Structure du projet

```
.
├── pom.xml
├── README.md
└── src
   └── main
      └── java
         └── fr
            └── fxjavadevblog
               └── mvnlmbkjdoc
                  ├── garage
                  │  ├── Garage.java
                  │  └── package-info.java
                  └── vehicules
                     ├── Energy.java
                     ├── package-info.java
                     ├── Vehicule.java
                     └── VehiculeUtils.java
```

## Classes

- **Vehicule** : Classe avec @Builder, @Getter, @EqualsAndHashCode, @ToString
- **Garage** : Classe avec @Builder, @ToString
- **VehiculeUtils** : Classe utilitaire avec @UtilityClass
- **Energy** : Enum simple

## Génération de la Javadoc

Pour générer la Javadoc avec prise en compte des annotations Lombok :

```bash
mvn clean compile javadoc:javadoc -Pjavadoc
```

Le profil `javadoc` active le plugin delombok qui génère le code source développé à partir des annotations Lombok, puis le plugin javadoc utilise ce code généré.

## Build normal

Pour un build classique sans génération de javadoc :

```bash
mvn clean compile
```

## Vérification

La Javadoc générée contiendra :
- Les méthodes du builder pour Vehicule et Garage
- Les getters générés par Lombok
- Les méthodes utilitaires de VehiculeUtils
- Les constructeurs privés générés par @Builder

Sans le profil javadoc, la Javadoc serait incomplète et contiendrait des warnings.
