# Projet : Intégration Dahua DSS Professional

## Objectif

Développer une application Spring Boot permettant de communiquer avec un serveur Dahua DSS Professional afin de récupérer les données People Counting via les API officielles REST.

L'application devra ensuite synchroniser ces données dans une base MySQL afin de générer des tableaux de bord et des rapports indépendamment du serveur DSS.

Aucune lecture directe de la base DSS ne sera utilisée.

Nous utiliserons exclusivement les API officielles Dahua.

---

# Architecture cible

Spring Boot 3.x

Java 21

Maven

Architecture en couches :

Controller

↓

Service

↓

Client DSS

↓

API REST DSS

↓

Serveur DSS

↓

Base locale MySQL

---

# Première étape

La première fonctionnalité consiste uniquement à établir une connexion avec le serveur DSS.

Objectif :

- Lire les paramètres DSS depuis application.yml
- Se connecter au serveur DSS
- Authentifier un utilisateur DSS
- Récupérer le X-Subject-Token
- Stocker ce token
- Vérifier que l'API est accessible

Aucun autre développement ne doit commencer avant cette étape.

---

# Configuration

application.yml

dss:

protocol:

host:

port:

username:

password:

verifySsl:

---

# Technologies

- Spring Boot Web
- Lombok
- Jackson
- Spring Validation

Plus tard :

- Spring Data JPA
- MySQL
- Spring Retry

---

# Architecture des packages

com.company.dss

config

controller

service

client

dto

exception

util

model

peoplecounting

mq

authentication

common

---

# DssClient

Créer une classe DssClient.

Cette classe sera le client HTTP unique de toute l'application.

Toutes les API Dahua passeront par cette classe.

Elle devra proposer :

GET

POST

PUT

DELETE

et gérer automatiquement :

- X-Subject-Token
- erreurs HTTP
- timeout
- logs

---

# Authentication

Créer :

AuthenticationClient

AuthenticationService

AuthenticationController

Responsabilités :

- login
- logout
- renouvellement du token

Le token doit être conservé dans un TokenHolder.

---

# Endpoint de test

Créer

GET /api/test/login

Ce endpoint doit :

1. appeler le login DSS

2. récupérer le token

3. retourner

{
    "connected": true,
    "token":"..."
}

---

# Modules futurs

Une fois l'authentification validée :

People Counting

↓

Passenger Flow Groups

↓

History

↓

Statistics

↓

Synchronisation MySQL

↓

Dashboard

↓

Rapports PDF

↓

Rapports Excel

---

# Contraintes

Le code doit être professionnel.

Respecter SOLID.

Favoriser l'injection de dépendances.

Ne jamais dupliquer le code.

Créer des interfaces pour les services.

Centraliser tous les appels HTTP.

Toutes les URLs DSS doivent être construites automatiquement à partir du baseUrl.

Toutes les exceptions doivent être centralisées.

Les logs doivent être explicites.

---

# Ce qu'il ne faut PAS faire

Ne jamais accéder directement à la base DSS.

Ne jamais coder des URLs en dur.

Ne jamais utiliser de variables globales.

Ne jamais mettre de logique métier dans les contrôleurs.

---

# Évolution prévue

Après People Counting, le même SDK devra permettre d'intégrer :

- LPR

- Access Control

- Alarm Center

- Face Recognition

- MQ Notification

Le projet doit donc être conçu comme un SDK DSS réutilisable.