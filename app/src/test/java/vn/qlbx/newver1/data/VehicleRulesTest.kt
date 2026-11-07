package vn.qlbx.newver1.data

import org.junit.Assert.*
import org.junit.Test

class VehicleRulesTest {
    @Test fun vinMustContainExactlySixDigits() {
        assertTrue(VehicleRules.validate(VehicleDraft(IdentityType.VIN6, "12345")) is VehicleValidation.Invalid)
        assertTrue(VehicleRules.validate(VehicleDraft(IdentityType.VIN6, "12A456")) is VehicleValidation.Invalid)
        assertTrue(VehicleRules.validate(VehicleDraft(IdentityType.VIN6, "123456")) is VehicleValidation.Valid)
    }

    @Test fun onlyIdentifierIsRequired() {
        val result = VehicleRules.validate(VehicleDraft(IdentityType.PLATE, " 51A-123.45 "))
        assertTrue(result is VehicleValidation.Valid)
        val vehicle = (result as VehicleValidation.Valid).vehicle
        assertEquals("51A123.45", vehicle.plate)
        assertEquals("", vehicle.model)
    }

    @Test fun duplicateActiveIdentifierIsBlockedButExitedRecordDoesNotBlock() {
        val old = Vehicle(identityType = IdentityType.VIN6, vinLast6 = "123456")
        val same = Vehicle(identityType = IdentityType.VIN6, vinLast6 = "123456")
        assertTrue(VehicleRules.isDuplicateActive(same, listOf(old)))
        assertFalse(VehicleRules.isDuplicateActive(same, listOf(old.copy(status = VehicleStatus.EXITED))))
    }
}
