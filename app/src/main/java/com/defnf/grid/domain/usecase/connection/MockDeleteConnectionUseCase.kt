package com.defnf.grid.domain.usecase.connection

import com.defnf.grid.domain.model.Result
import javax.inject.Inject

class MockDeleteConnectionUseCase @Inject constructor() {
    suspend operator fun invoke(): Result<Unit> {
        return Result.Success(Unit)
    }
}