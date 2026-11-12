package com.ridecomp.core.config

/** Power windows and NP (05-DATA-MODEL §3.6). */
public object PowerConfig {
    public const val SHORT_WINDOW_S: Int = 3
    public const val MEDIUM_WINDOW_S: Int = 10

    /** NP uses a 30 s rolling average raised to the 4th power. */
    public const val NP_WINDOW_S: Int = 30
    public const val NP_EXPONENT: Double = 4.0

    /** Power zone upper bounds in % FTP for Z1..Z6; above the last is Z7 (§3.9). */
    public val ZONE_UPPER_PERCENT_FTP: List<Double> = listOf(55.0, 75.0, 90.0, 105.0, 120.0, 150.0)
}

/** Heart rate zones (05-DATA-MODEL §3.8): upper bounds in % HR max for Z1..Z4; above is Z5. */
public object HeartRateConfig {
    public val ZONE_UPPER_PERCENT_HR_MAX: List<Double> = listOf(60.0, 70.0, 80.0, 90.0)
}

/** Estimated power physics model (05-DATA-MODEL §3.7). */
public object EstimatedPowerConfig {
    public const val GRAVITY_MPS2: Double = 9.81
    public const val AIR_DENSITY_KG_M3: Double = 1.16
    public const val DRIVETRAIN_EFFICIENCY: Double = 0.97
    public const val CRR_ROAD: Double = 0.005
    public const val CRR_GRAVEL: Double = 0.008
    public const val CDA_TOPS_M2: Double = 0.40
    public const val CDA_HOODS_M2: Double = 0.32
    public const val CDA_DROPS_M2: Double = 0.27

    /** Smoothing of acceleration dv/dt to keep GPS noise out of the estimate. */
    public const val ACCELERATION_EMA_ALPHA: Double = 0.2
}

/** Calories (05-DATA-MODEL §3.10). */
public object CalorieConfig {
    /** 1 kJ of mechanical work ≈ 1 kcal metabolic (≈ 24 % efficiency). */
    public const val KCAL_PER_KJ: Double = 1.0
    public const val JOULES_PER_KJ: Double = 1_000.0
    public const val KJ_PER_KCAL: Double = 4.184

    // Keytel et al. (2005) per-minute energy expenditure coefficients.
    public const val MALE_INTERCEPT: Double = -55.0969
    public const val MALE_HR: Double = 0.6309
    public const val MALE_WEIGHT: Double = 0.1988
    public const val MALE_AGE: Double = 0.2017
    public const val FEMALE_INTERCEPT: Double = -20.4022
    public const val FEMALE_HR: Double = 0.4472
    public const val FEMALE_WEIGHT: Double = -0.1263
    public const val FEMALE_AGE: Double = 0.074
}
