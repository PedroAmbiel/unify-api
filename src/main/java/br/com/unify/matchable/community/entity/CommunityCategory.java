package br.com.unify.matchable.community.entity;

import java.util.List;

import br.com.unify.matchable.user.entity.LookupEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "community_categories")
public class CommunityCategory extends LookupEntity {

    @Column(name = "ionic_icon", length = 60)
    public String ionicIcon;

    /** Opções para criar/editar comunidade: só as ativas (semana 06). */
    public static List<CommunityCategory> listActiveOrderedByDescription() {
        return list("active = true order by description asc");
    }
}
