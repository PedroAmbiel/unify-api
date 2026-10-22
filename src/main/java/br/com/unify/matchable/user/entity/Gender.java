package br.com.unify.matchable.user.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "genders")
public class Gender extends LookupEntity {

    /**
     * "Prefiro não informar": o discovery sempre inclui este gênero, seja qual
     * for a preferência. O backoffice não deixa desativar nem excluir.
     */
    public static final int UNDISCLOSED_ID = 4;
}
