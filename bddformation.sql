-- phpMyAdmin SQL Dump
-- version 5.2.3
-- https://www.phpmyadmin.net/
--
-- Hôte : 127.0.0.1:3306
-- Généré le : lun. 26 oct. 2026 à 15:07
-- Version du serveur : 5.7.36
-- Version de PHP : 8.5.5

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Base de données : `bddformation`
--

-- --------------------------------------------------------

--
-- Structure de la table `doctrine_migration_versions`
--

DROP TABLE IF EXISTS `doctrine_migration_versions`;
CREATE TABLE IF NOT EXISTS `doctrine_migration_versions` (
  `version` varchar(191) COLLATE utf8_unicode_ci NOT NULL,
  `executed_at` datetime DEFAULT NULL,
  `execution_time` int(11) DEFAULT NULL,
  PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- --------------------------------------------------------

--
-- Structure de la table `employe`
--

DROP TABLE IF EXISTS `employe`;
CREATE TABLE IF NOT EXISTS `employe` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `login` varchar(50) COLLATE utf8_unicode_ci NOT NULL,
  `mdp` varchar(50) COLLATE utf8_unicode_ci NOT NULL,
  `nom` varchar(50) COLLATE utf8_unicode_ci NOT NULL,
  `prenom` varchar(50) COLLATE utf8_unicode_ci NOT NULL,
  `statut` int(11) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

--
-- Déchargement des données de la table `employe`
--

INSERT INTO `employe` (`id`, `login`, `mdp`, `nom`, `prenom`, `statut`) VALUES
(1, 'martin', '2f7d7b2bfa5fcf0093613eeca35e998a', 'Dupond', 'Martin', 0),
(2, 'gerard', '8d7291fd96a1165f38d6c7ea4a84868b', 'Durand', 'Gérard', 0),
(4, 'Fernand', 'cbae0d9b57891c1a328fa4669a84fc2a', 'Fernand', 'Sanchez', 1);

-- --------------------------------------------------------

--
-- Structure de la table `formation`
--

DROP TABLE IF EXISTS `formation`;
CREATE TABLE IF NOT EXISTS `formation` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `date_debut` date NOT NULL,
  `nbre_heures` int(11) NOT NULL,
  `departement` varchar(50) COLLATE utf8_unicode_ci NOT NULL,
  `produit_id` int(11) DEFAULT NULL,
  `min_inscrits` int(11) NOT NULL,
  `description` varchar(50) COLLATE utf8_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `IDX_404021BFF347EFB` (`produit_id`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

--
-- Déchargement des données de la table `formation`
--

INSERT INTO `formation` (`id`, `date_debut`, `nbre_heures`, `departement`, `produit_id`, `min_inscrits`, `description`) VALUES
(1, '2026-10-21', 5, 'Paris', 4, 0, 'Initiation à l\'imprimante'),
(3, '2026-11-26', 7, 'Loiret', 3, 0, 'Utilisation d\'un pc portable'),
(4, '2027-05-07', 10, 'Grand Est', 2, 0, 'Utilisation d\'un PC de bureau'),
(5, '2027-05-07', 10, 'Belfort', 5, 0, 'Les fonctions de l\'imprimante 3D'),
(6, '2027-09-09', 2, 'Ile-de-France', 1, 0, 'Installation de votre tablette'),
(10, '2027-03-31', 12, 'Alpes', 4, 3, 'Utilisation de l\'imprimante'),
(11, '2022-04-05', 3, 'La Rochelle', 3, 2, 'Installation d\'un pare-feu'),
(12, '2022-05-06', 2, 'Lyon', 4, 2, 'Utilisation de l\'imprimante');

-- --------------------------------------------------------

--
-- Structure de la table `inscription`
--

DROP TABLE IF EXISTS `inscription`;
CREATE TABLE IF NOT EXISTS `inscription` (
  `employe_id` int(11) DEFAULT NULL,
  `statut` varchar(50) COLLATE utf8_unicode_ci NOT NULL,
  `formation_id` int(11) DEFAULT NULL,
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `commentaire` varchar(60) COLLATE utf8_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `IDX_5E90F6D65200282E` (`formation_id`),
  KEY `IDX_5E90F6D61B65292` (`employe_id`)
) ENGINE=InnoDB AUTO_INCREMENT=46 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

--
-- Déchargement des données de la table `inscription`
--

INSERT INTO `inscription` (`employe_id`, `statut`, `formation_id`, `id`, `commentaire`) VALUES
(2, 'Refusé', 6, 28, NULL),
(2, 'Refusé', 1, 29, NULL),
(4, 'Refusé', 1, 31, NULL),
(1, 'Refusé', 10, 32, 'Ne correspond pas au profil'),
(1, 'Refusé', 6, 33, NULL),
(1, 'Refusé', 11, 34, 'Ne correspond pas au profil'),
(2, 'Refusé', 1, 35, NULL),
(2, 'Refusé', 1, 36, NULL),
(2, 'Refusé', 1, 37, NULL),
(2, 'Refusé', 3, 38, NULL),
(2, 'Refusé', 4, 39, NULL),
(2, 'Refusé', 5, 40, NULL),
(2, 'Refusé', 6, 41, NULL),
(2, 'Refusé', 10, 43, NULL),
(2, 'Refusé', 11, 44, 'Test'),
(2, 'Refusé', 12, 45, NULL);

-- --------------------------------------------------------

--
-- Structure de la table `produit`
--

DROP TABLE IF EXISTS `produit`;
CREATE TABLE IF NOT EXISTS `produit` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `libelle` varchar(50) COLLATE utf8_unicode_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

--
-- Déchargement des données de la table `produit`
--

INSERT INTO `produit` (`id`, `libelle`) VALUES
(1, 'Tablette numérique'),
(2, 'Ordinateur'),
(3, 'PC portable'),
(4, 'Imprimante'),
(5, 'Imprimante 3D');

-- --------------------------------------------------------

--
-- Structure de la table `service`
--

DROP TABLE IF EXISTS `service`;
CREATE TABLE IF NOT EXISTS `service` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `employe_id` int(11) DEFAULT NULL,
  `produit_id` int(11) DEFAULT NULL,
  `nom` varchar(55) COLLATE utf8_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `IDX_E19D9AD21B65292` (`employe_id`),
  KEY `IDX_E19D9AD2F347EFB` (`produit_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

--
-- Déchargement des données de la table `service`
--

INSERT INTO `service` (`id`, `employe_id`, `produit_id`, `nom`) VALUES
(1, 2, 1, 'Pharmacie');

--
-- Contraintes pour les tables déchargées
--

--
-- Contraintes pour la table `formation`
--
ALTER TABLE `formation`
  ADD CONSTRAINT `FK_404021BFF347EFB` FOREIGN KEY (`produit_id`) REFERENCES `produit` (`id`);

--
-- Contraintes pour la table `inscription`
--
ALTER TABLE `inscription`
  ADD CONSTRAINT `FK_5E90F6D61B65292` FOREIGN KEY (`employe_id`) REFERENCES `employe` (`id`),
  ADD CONSTRAINT `FK_5E90F6D65200282E` FOREIGN KEY (`formation_id`) REFERENCES `formation` (`id`);

--
-- Contraintes pour la table `service`
--
ALTER TABLE `service`
  ADD CONSTRAINT `FK_E19D9AD21B65292` FOREIGN KEY (`employe_id`) REFERENCES `employe` (`id`),
  ADD CONSTRAINT `FK_E19D9AD2F347EFB` FOREIGN KEY (`produit_id`) REFERENCES `produit` (`id`);
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
