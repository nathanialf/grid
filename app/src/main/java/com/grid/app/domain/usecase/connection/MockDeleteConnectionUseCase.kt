package com.grid.app.domain.usecase.connection

import com.grid.app.domain.model.Result
import javax.inject.Inject

class MockDeleteConnectionUseCase @Inject constructor() {
    suspend operator fun invoke(connectionId: String): Result<Unit> {
        return Result.Success(Unit)
    }
}