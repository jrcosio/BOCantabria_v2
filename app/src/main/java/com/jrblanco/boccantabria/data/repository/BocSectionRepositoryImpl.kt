package com.jrblanco.boccantabria.data.repository

import com.jrblanco.boccantabria.domain.model.BocSection
import com.jrblanco.boccantabria.domain.model.SectionColorGroup
import com.jrblanco.boccantabria.domain.repository.BocSectionRepository

/**
 * The official section tree, written out.
 *
 * The names are in Spanish and live in code rather than in string resources on purpose: they are
 * the official nomenclature of the bulletin, the same class of data as the source addresses, not
 * interface copy. There is nothing to translate — the Boletín Oficial de Cantabria publishes in
 * Spanish.
 *
 * Short names exist because the official ones do not fit in a filter chip. They are the only
 * part of this file that is an editorial decision.
 */
class BocSectionRepositoryImpl : BocSectionRepository {

    override fun sections(): List<BocSection> = SECTIONS

    private companion object {

        val SECTIONS: List<BocSection> = listOf(
            section("1", "Disposiciones generales", "Disposiciones", null, 1, SectionColorGroup.GENERAL),

            section("2", "Autoridades y personal", "Personal", null, 2, SectionColorGroup.PERSONNEL),
            section("2.1", "Nombramientos, ceses y otras situaciones", "Nombramientos", "2", 3, SectionColorGroup.PERSONNEL),
            section("2.2", "Cursos, oposiciones y concursos", "Oposiciones", "2", 4, SectionColorGroup.PERSONNEL),
            section("2.3", "Otros", "Otros de personal", "2", 5, SectionColorGroup.PERSONNEL),

            section("3", "Contratación administrativa", "Contratación", null, 6, SectionColorGroup.CONTRACTING),

            section("4", "Economía, Hacienda y Seguridad Social", "Economía", null, 7, SectionColorGroup.ECONOMY),
            section("4.1", "Actuaciones en materia presupuestaria", "Presupuestos", "4", 8, SectionColorGroup.ECONOMY),
            section("4.2", "Actuaciones en materia fiscal", "Fiscal", "4", 9, SectionColorGroup.ECONOMY),
            section("4.3", "Actuaciones en materia de Seguridad Social", "Seguridad Social", "4", 10, SectionColorGroup.ECONOMY),
            section("4.4", "Otros", "Otros de economía", "4", 11, SectionColorGroup.ECONOMY),

            section("5", "Expropiación forzosa", "Expropiación", null, 12, SectionColorGroup.ANNOUNCEMENTS),
            section("6", "Subvenciones y ayudas", "Subvenciones", null, 13, SectionColorGroup.ECONOMY),

            section("7", "Otros anuncios", "Anuncios", null, 14, SectionColorGroup.ANNOUNCEMENTS),
            section("7.1", "Urbanismo", "Urbanismo", "7", 15, SectionColorGroup.ANNOUNCEMENTS),
            section("7.2", "Medio ambiente y energía", "Medio ambiente", "7", 16, SectionColorGroup.ANNOUNCEMENTS),
            section("7.3", "Estatutos y convenios colectivos", "Convenios", "7", 17, SectionColorGroup.ANNOUNCEMENTS),
            section("7.4", "Particulares", "Particulares", "7", 18, SectionColorGroup.ANNOUNCEMENTS),
            section("7.5", "Varios", "Varios", "7", 19, SectionColorGroup.ANNOUNCEMENTS),

            section("8", "Procedimientos judiciales", "Judicial", null, 20, SectionColorGroup.ANNOUNCEMENTS),
            section("8.1", "Subastas", "Subastas", "8", 21, SectionColorGroup.ANNOUNCEMENTS),
            section("8.2", "Otros anuncios", "Otros judiciales", "8", 22, SectionColorGroup.ANNOUNCEMENTS),

            section("9", "Elecciones", "Elecciones", null, 23, SectionColorGroup.GENERAL),
        )

        @Suppress("LongParameterList")
        fun section(
            code: String,
            name: String,
            shortName: String,
            parentCode: String?,
            order: Int,
            colorGroup: SectionColorGroup,
        ) = BocSection(code, name, shortName, parentCode, order, colorGroup)
    }
}
