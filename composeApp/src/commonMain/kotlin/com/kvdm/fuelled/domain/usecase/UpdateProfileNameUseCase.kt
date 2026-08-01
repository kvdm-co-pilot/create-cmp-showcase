package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.domain.repository.ProfileRepository
import com.kvdm.fuelled.domain.result.AppResult

/** PERS-03: rename the user. Blankness is refused at the ViewModel; this is the write path. */
class UpdateProfileNameUseCase(
    private val repository: ProfileRepository,
) {
    suspend operator fun invoke(name: String): AppResult<Unit> = repository.updateName(name)
}
