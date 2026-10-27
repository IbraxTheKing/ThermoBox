package ax.ibr.thermobox.restserver.hierarchie

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.common.acces.Perimetre
import ax.ibr.thermobox.common.entities.Boitier
import ax.ibr.thermobox.common.entities.Salle
import ax.ibr.thermobox.common.entities.SalleKind
import ax.ibr.thermobox.common.entities.User
import ax.ibr.thermobox.common.entities.kind
import ax.ibr.thermobox.restserver.dto.ArbreDto
import ax.ibr.thermobox.restserver.dto.ConsigneDto
import ax.ibr.thermobox.restserver.dto.DetailDto
import ax.ibr.thermobox.restserver.dto.ElementDto
import ax.ibr.thermobox.restserver.dto.MesureBoitierDto
import ax.ibr.thermobox.restserver.dto.NoeudDto
import ax.ibr.thermobox.restserver.dto.iso
import ax.ibr.thermobox.restserver.dto.version

/**
 * The bâtiments, étages, salles and boxes as one user sees them, for one request: what they may
 * see (AccesService), their favourites, and the boxes only for a GESTIONNAIRE or an ADMIN (a
 * VIEWER sees bâtiments, étages, salles and their temperatures, not the hardware).
 *
 * An element outside the user's accesses is never described: [element] answers null for it,
 * exactly as for an unknown id.
 */
class VueUtilisateur(private val user: User, private val acces: Perimetre) {

    private val salles = BusinessFactory.getSalleService()
    private val hierarchie = BusinessFactory.getHierarchieService()
    private val climat = BusinessFactory.getClimatService()

    /** Boxes are hardware: GESTIONNAIRE and ADMIN only. */
    val boitiersVisibles: Boolean = user.type?.hasRole("GESTIONNAIRE") == true

    private val favoris: Set<Long> by lazy {
        BusinessFactory.getFavoriService().getByUser(user).mapNotNull { it.salle.id }.toSet()
    }

    private val images by lazy { BusinessFactory.getSalleImageService().getDates() }

    // Every link at once: one query rather than one per element.
    private val parents: Map<Long, Long> by lazy {
        BusinessFactory.getSalleSalleAttrService().getAll()
            .mapNotNull { l -> l.salleB.id?.let { enfant -> l.salleA.id?.let { parent -> enfant to parent } } }
            .toMap()
    }

    /** Shown at all: allowed, or an ancestor of something allowed (name only). */
    fun visible(s: Salle): Boolean = acces.estVisible(s) && (s !is Boitier || boitiersVisibles)

    /** Its data may be read: allowed (and not a box for a VIEWER). */
    fun accessible(s: Salle): Boolean = acces.peutVoir(s) && (s !is Boitier || boitiersVisibles)

    /** The element [id] if this user may open it, null otherwise (same answer as for an unknown id). */
    fun element(id: Long): Salle? = salles.getById(id)?.takeIf { accessible(it) }

    fun arbre(): ArbreDto {
        val noeuds = salles.getAll().filter { it.id != null && visible(it) }
            .sortedWith(compareBy({ it.kind }, { it.name?.lowercase() }))
            .map { noeud(it) }
        return ArbreDto(acces.tout, boitiersVisibles, noeuds)
    }

    fun noeud(s: Salle): NoeudDto {
        val parentId = parents[s.id]
        val rattache = parentId != null || s.kind == SalleKind.BATIMENT
        if (!accessible(s)) {
            return NoeudDto(s.id, s.name, s.kind.name, parentId, rattache, false, null, null, 0, null, null, false)
        }
        val mesures = climat.getMesures(s)
        val valeurs = mesures.mapNotNull { it.mesure?.value }
        val temperature = if (s is Boitier) valeurs.firstOrNull() else valeurs.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        return NoeudDto(
            id = s.id,
            name = s.name,
            kind = s.kind.name,
            parentId = parentId,
            rattache = rattache,
            accessible = true,
            temperature = temperature,
            derniereMesure = iso(mesures.mapNotNull { it.mesure?.date }.maxOrNull()),
            boitiers = if (boitiersVisibles) mesures.size else 0,
            consigne = if (s is Boitier) null else climat.getConsigneEffective(s)?.let { ConsigneDto.of(it) },
            image = images[s.id]?.let { version(it) },
            favori = s.id in favoris,
        )
    }

    fun detail(s: Salle): DetailDto {
        val boitier = s is Boitier
        val conteneur = s.kind == SalleKind.ETAGE || s.kind == SalleKind.BATIMENT
        return DetailDto(
            element = noeud(s),
            ancetres = hierarchie.getChemin(s).map { ElementDto.of(it, accessible(it)) },
            enfants = if (boitier) emptyList() else hierarchie.getChildren(s).filter { visible(it) }.map { ElementDto.of(it, accessible(it)) },
            mesures = if (!boitiersVisibles) emptyList() else climat.getMesures(s).map {
                MesureBoitierDto(it.boitier.id, it.boitier.name, it.mesure?.value, iso(it.mesure?.date))
            },
            consignesPropres = if (boitier) emptyList() else climat.getConsignesPropres(s).map { ConsigneDto.of(it) },
            salles = if (conteneur) hierarchie.getSalles(s).filter { accessible(it) }.map { noeud(it) } else emptyList(),
        )
    }
}
