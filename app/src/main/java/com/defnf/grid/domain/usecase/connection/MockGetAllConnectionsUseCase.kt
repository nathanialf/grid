package com.defnf.grid.domain.usecase.connection

import com.defnf.grid.domain.model.Connection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class MockGetAllConnectionsUseCase @Inject constructor() {
    operator fun invoke(): Flow<List<Connection>> {
        return flowOf(emptyList())
    }
}