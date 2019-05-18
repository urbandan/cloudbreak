package com.sequenceiq.environment.environment.repository.network;

import javax.transaction.Transactional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sequenceiq.environment.environment.domain.network.BaseNetwork;

@Transactional(Transactional.TxType.REQUIRED)
public interface BaseNetworkRepository<T extends BaseNetwork> extends JpaRepository<T, Long> {
}
