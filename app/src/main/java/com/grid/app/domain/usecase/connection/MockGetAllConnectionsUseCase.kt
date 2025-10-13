package com.grid.app.domain.usecase.connection

import com.grid.app.domain.model.Connection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class MockGetAllConnectionsUseCase @Inject constructor() {
    operator fun invoke(): Flow<List<Connection>> {
        return flowOf(emptyList())
    }
}