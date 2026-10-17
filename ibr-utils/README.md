## ibr-utils Module

Le module `ibr-utils` contient les utilitaires transversaux utilisés par l'ensemble du projet ThermoBox. Il est conçu pour être indépendant et réutilisable.

### Fonctionnalités principales :
- Logging : Implémentation de `JsonLogger` pour une journalisation structurée en format JSON, facilitant l'intégration avec des systèmes comme ELK ou Splunk.
- Cryptographie : Fonctions utilitaires pour la gestion des secrets et le hachage (utilisé notamment pour la sécurité JWT).
- Exceptions : Définition d'une hiérarchie d'exceptions personnalisées pour une gestion d'erreurs propre à l'application.

### Utilisation :
Ce module est inclus automatiquement dans les autres modules via la dépendance `ax.ibr:ibr-utils`. Il fournit des classes statiques et des utilitaires simples pour le logging et la sécurité.