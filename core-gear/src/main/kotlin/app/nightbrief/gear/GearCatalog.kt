package app.nightbrief.gear

/** Common astrophotography bodies and lenses offered in the gear picker. Users can also enter custom gear. */
object GearCatalog {
    val canonR7 = CameraBody("canon-r7", "Canon EOS R7", 22.3, 14.8, 32.5)
    val sigma10to18 = Lens("sigma-10-18-f28-dcdn", "Sigma 10–18mm f/2.8 DC DN Contemporary", 10.0, 18.0, 2.8)

    val bodies: List<CameraBody> = listOf(
        canonR7,
        CameraBody("canon-r10", "Canon EOS R10", 22.3, 14.8, 24.2),
        CameraBody("canon-r6m2", "Canon EOS R6 Mark II", 35.9, 23.9, 24.2),
        CameraBody("canon-r5", "Canon EOS R5", 36.0, 24.0, 45.0),
        CameraBody("canon-ra", "Canon EOS Ra", 36.0, 24.0, 30.3),
        CameraBody("canon-6d", "Canon EOS 6D", 35.8, 23.9, 20.2),
        CameraBody("canon-t7", "Canon EOS Rebel T7 / 2000D", 22.3, 14.9, 24.1),
        CameraBody("sony-a7iii", "Sony α7 III", 35.6, 23.8, 24.2),
        CameraBody("sony-a7iv", "Sony α7 IV", 35.9, 23.9, 33.0),
        CameraBody("sony-a7siii", "Sony α7S III", 35.6, 23.8, 12.1),
        CameraBody("sony-a6400", "Sony α6400", 23.5, 15.6, 24.2),
        CameraBody("nikon-z6ii", "Nikon Z6 II", 35.9, 23.9, 24.5),
        CameraBody("nikon-z5", "Nikon Z5", 35.9, 23.9, 24.3),
        CameraBody("nikon-d750", "Nikon D750", 35.9, 24.0, 24.3),
        CameraBody("nikon-z50", "Nikon Z50", 23.5, 15.7, 20.9),
        CameraBody("fuji-xt5", "Fujifilm X-T5", 23.5, 15.6, 40.2),
        CameraBody("fuji-xt4", "Fujifilm X-T4", 23.5, 15.6, 26.1),
        CameraBody("om-1", "OM System OM-1", 17.4, 13.0, 20.4),
    )

    val lenses: List<Lens> = listOf(
        sigma10to18,
        Lens("sigma-16-f14-dcdn", "Sigma 16mm f/1.4 DC DN Contemporary", 16.0, maxAperture = 1.4),
        Lens("viltrox-13-f14", "Viltrox AF 13mm f/1.4", 13.0, maxAperture = 1.4),
        Lens("tokina-11-16-f28", "Tokina 11–16mm f/2.8", 11.0, 16.0, 2.8),
        Lens("samyang-12-f2", "Samyang 12mm f/2 NCS CS", 12.0, maxAperture = 2.0),
        Lens("samyang-14-f28", "Samyang / Rokinon 14mm f/2.8", 14.0, maxAperture = 2.8),
        Lens("sigma-14-f18-art", "Sigma 14mm f/1.8 DG HSM Art", 14.0, maxAperture = 1.8),
        Lens("sigma-14-24-f28-dgdn", "Sigma 14–24mm f/2.8 DG DN Art", 14.0, 24.0, 2.8),
        Lens("laowa-15-f2", "Laowa 15mm f/2 Zero-D", 15.0, maxAperture = 2.0),
        Lens("sigma-20-f14-dgdn", "Sigma 20mm f/1.4 DG DN Art", 20.0, maxAperture = 1.4),
        Lens("sigma-24-f14-art", "Sigma 24mm f/1.4 DG HSM Art", 24.0, maxAperture = 1.4),
        Lens("samyang-24-f14", "Samyang / Rokinon 24mm f/1.4", 24.0, maxAperture = 1.4),
        Lens("kit-18-55", "18–55mm f/3.5–5.6 kit zoom", 18.0, 55.0, 3.5, 5.6),
        Lens("rf-24-105-f4", "Canon RF 24–105mm f/4L", 24.0, 105.0, 4.0),
        Lens("nifty-50-f18", "50mm f/1.8", 50.0, maxAperture = 1.8),
        Lens("samyang-135-f2", "Samyang / Rokinon 135mm f/2", 135.0, maxAperture = 2.0),
        Lens("tele-70-200-f28", "70–200mm f/2.8", 70.0, 200.0, 2.8),
        Lens("tele-100-400", "100–400mm f/4.5–5.6", 100.0, 400.0, 4.5, 5.6),
    )

    /** Example profile pre-seeded on first launch. */
    val exampleKit: GearKit = GearKit(
        bodies = listOf(canonR7),
        lenses = listOf(sigma10to18),
        primaryBodyId = canonR7.id,
    )
}
