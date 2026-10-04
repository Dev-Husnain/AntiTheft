package com.anti.theft.domain.usecase

import com.anti.theft.domain.repo.ProtectionRepository
import kotlinx.coroutines.flow.first

class SyncProtectionUseCase(
    private val observeEffectiveProtection: ObserveEffectiveProtectionUseCase,
    private val protection: ProtectionRepository,
) {
    suspend operator fun invoke(): Result<Unit> =
        protection.apply(observeEffectiveProtection().first())
}
