# Forma — Gestion des formations

Application web de gestion des formations et des inscriptions des employés. Elle propose un espace employé et des outils d’administration pour gérer les formations, les inscriptions et les affectations aux services.

## Fonctionnalités

### Employés
- Créer un compte et se connecter.
- Consulter les formations disponibles.
- S’inscrire à une formation ou annuler une inscription lorsque la formation n’a pas commencé.
- Consulter le statut et les commentaires de ses inscriptions.

### Administration
- Créer et supprimer des formations.
- Consulter les inscriptions en cours et les accepter ou les refuser.
- Retirer une ou plusieurs inscriptions acceptées.
- Modifier le statut d’un employé.
- Créer et supprimer des services.
- Affecter un employé existant à un service et à un produit, puis libérer cette affectation.

## Technologies

- Java 27
- Spring Boot 4.1.1
- Spring MVC et JDBC
- MySQL
- Maven Wrapper
- HTML, CSS et JavaScript

## Prérequis

- Java 27
- MySQL
- Aucun Maven global n’est nécessaire : le projet inclut le Maven Wrapper.

## Configuration

L’application utilise une base MySQL nommée `bddformation`, accessible par défaut à l’adresse `localhost:3306`.

Configurez ces variables d’environnement avant de lancer l’application :

| Variable | Description |
|---|---|
| `DB_HOST` | Serveur |
| `DB_USER` | Utilisateur MySQL |
| `DB_PASSWORD` | Mot de passe MySQL |
| `DB_NAME` | Nom de la base de données |

Le fichier bddformation.sql présent à la racine du projet permet de créer la base de données avec des données par défault.

Listes des comptes utilisateur de test par défaut :

| Identifiant | Mot de passe |
|---|---|
| martin | m2rt1n |
| gerard | g3r2rd |
| fernand | fernand20 |


## Lancer l’application

À la racine du projet, sous Windows :

```powershell
.\mvnw.cmd spring-boot:run
```

L’application est ensuite accessible à l’adresse :

```text
http://localhost:8080
```

Les pages principales sont :

- `/` — connexion
- `/inscription.html` — création de compte
- `/employe.html` — espace employé et, selon les droits, administration

## API

| Méthode | Route | Description |
|---|---|---|
| `GET` | `/api/csrf` | Obtenir un jeton CSRF |
| `POST` | `/api/login` | Se connecter |
| `POST` | `/api/register` | Créer un compte |
| `POST` | `/api/logout` | Se déconnecter |
| `GET` | `/api/employe/accueil` | Charger le tableau de bord |
| `POST` | `/api/employe/formations/{formationId}/inscription` | S’inscrire à une formation |
| `DELETE` | `/api/employe/formations/{formationId}/inscription` | Annuler une inscription |
| `POST` | `/api/employe/admin/formations` | Créer une formation |
| `DELETE` | `/api/employe/admin/formations/{formationId}` | Supprimer une formation |
| `PATCH` | `/api/employe/admin/inscriptions/{enrollmentId}` | Traiter une inscription |
| `PATCH` | `/api/employe/admin/employes/{employeeId}/statut` | Modifier le statut d’un employé |
| `POST` | `/api/employe/admin/services` | Créer un service |
| `DELETE` | `/api/employe/admin/services?nom={nom}` | Supprimer un service |
| `POST` | `/api/employe/admin/affectations` | Affecter un employé à un service |
| `DELETE` | `/api/employe/admin/affectations/{assignmentId}` | Libérer une affectation |

Les routes qui modifient des données nécessitent un jeton CSRF envoyé dans l’en-tête `X-CSRF-TOKEN`. Les opérations d’administration sont réservées aux employés dont le statut `employe.statut` vaut `1`.

## Tests

Exécuter les tests avec le Maven Wrapper :

```powershell
.\mvnw.cmd test
```

Compiler sans lancer les tests :

```powershell
.\mvnw.cmd -DskipTests compile
```

## Sécurité

- Les sessions expirent après 15 minutes.
- Le cookie de session est configuré avec `HttpOnly` et `SameSite=Strict`.
- Les actions sensibles sont protégées par un jeton CSRF.
- Le statut administrateur est vérifié en base lors des requêtes d’administration.
- **Les mots de passe sont actuellement stockés au format MD5.** 
