package com.logistpulse.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.logistpulse.lab.api.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class OrderSortingTest {

    @Test
    void sinParametroOrdenaPorIdDescendente() {
        assertThat(OrderSorting.parse(null)).isEqualTo(Sort.by(Sort.Direction.DESC, "id"));
        assertThat(OrderSorting.parse("  ")).isEqualTo(Sort.by(Sort.Direction.DESC, "id"));
    }

    @Test
    void camposPermitidosSeTraducenYLlevanDesempatePorId() {
        Sort s = OrderSorting.parse("createdAt,desc");
        assertThat(s).isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        // el nombre público "customer" se mapea a la propiedad real customerName
        assertThat(OrderSorting.parse("customer,ASC").getOrderFor("customerName")).isNotNull();
    }

    @Test
    void direccionPorDefectoEsAscendente() {
        assertThat(OrderSorting.parse("code").getOrderFor("code").isAscending()).isTrue();
    }

    @Test
    void campoFueraDeLaListaBlancaDa400() {
        assertThatThrownBy(() -> OrderSorting.parse("version,asc"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(400));
        assertThatThrownBy(() -> OrderSorting.parse("warehouse.name"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void direccionInvalidaOFormatoRaroDa400() {
        assertThatThrownBy(() -> OrderSorting.parse("id,sideways")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> OrderSorting.parse("id,asc,extra")).isInstanceOf(ApiException.class);
    }
}
