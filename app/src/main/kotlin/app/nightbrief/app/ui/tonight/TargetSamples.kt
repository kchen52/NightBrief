package app.nightbrief.app.ui.tonight

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.nightbrief.app.R
import app.nightbrief.app.ui.theme.NightColors
import app.nightbrief.app.ui.theme.NightPalette

/**
 * A resized photograph of one catalog target.
 *
 * [credit] is the line under the photo. [sourceUrl] is the Wikimedia Commons page for the
 * original; opening it is the licence link. The JPEG in the app is a smaller copy of that file.
 * Night vision draws the photo in red so a colour image does not spoil dark adaptation.
 */
data class TargetSample(
    @DrawableRes val drawable: Int,
    val credit: String,
    val sourceUrl: String,
)

/**
 * Bundled sample photos for [app.nightbrief.astro.TargetCatalog].
 *
 * [of] is null for a user-added target, which has no photograph. Every catalog id has one.
 * The same credit and page are listed under Attribution in the README.
 */
object TargetSamples {
    fun of(targetId: String): TargetSample? = byId[targetId]

    private fun sample(@DrawableRes drawable: Int, credit: String, sourceUrl: String) =
        TargetSample(drawable, credit, sourceUrl)

    private val byId: Map<String, TargetSample> = mapOf(
        "mw-core" to sample(R.drawable.target_mw_core, "Astrobond (CC BY-SA 4.0)", "https://commons.wikimedia.org/wiki/File:The_Milky_Way_in_the_mountains_of_the_Karaganda_region,_Kazakhstan.jpg"),
        "cygnus" to sample(R.drawable.target_cygnus, "A. Fujii (public domain)", "https://commons.wikimedia.org/wiki/File:Milky-Way_towards_the_constellation_of_Cygnus_(ground-based_image)_(heic0712h).jpg"),
        "m31" to sample(R.drawable.target_m31, "Taavi Niittee (CC0)", "https://commons.wikimedia.org/wiki/File:Andromeda_galaxy_2023.jpg"),
        "m42" to sample(R.drawable.target_m42, "NASA/ESA Hubble (public domain)", "https://commons.wikimedia.org/wiki/File:Orion_Nebula_-_Hubble_2006_mosaic_18000.jpg"),
        "m45" to sample(R.drawable.target_m45, "NASA, ESA and Palomar Observatory (public domain)", "https://commons.wikimedia.org/wiki/File:Pleiades_large.jpg"),
        "ngc7000" to sample(R.drawable.target_ngc7000, "NASA/JPL-Caltech (public domain)", "https://commons.wikimedia.org/wiki/File:North_America_Nebula.jpg"),
        "m8" to sample(R.drawable.target_m8, "Sebas FL (CC BY-SA 4.0)", "https://commons.wikimedia.org/wiki/File:The_Lagoon_and_Trifid_Nebulae_(Canon_EOS_250D_%26_75-300_F4-5.6).jpg"),
        "double-cluster" to sample(R.drawable.target_double_cluster, "Taavi Niittee (CC0)", "https://commons.wikimedia.org/wiki/File:Double_cluster.jpg"),
        "m13" to sample(R.drawable.target_m13, "Carsten Frenzl (CC BY 2.0)", "https://commons.wikimedia.org/wiki/File:Messier_13_-_The_Hercules_Globular_Cluster_-_Flickr_-_castro.pic.jpg"),
        "orion" to sample(R.drawable.target_orion, "Taavi Niittee (CC0)", "https://commons.wikimedia.org/wiki/File:Orion_constellation.jpg"),
        "big-dipper" to sample(R.drawable.target_big_dipper, "Great Sand Dunes National Park and Preserve (public domain)", "https://commons.wikimedia.org/wiki/File:Big_Dipper_Constellation_Over_Dunes_(38836718054).jpg"),
        "polaris-trails" to sample(R.drawable.target_polaris_trails, "Heyzeuss (CC BY-SA 3.0)", "https://commons.wikimedia.org/wiki/File:Circumpolar_star_trails.jpg"),
        "ngc3372" to sample(R.drawable.target_ngc3372, "ESO/T. Preibisch (CC BY 4.0)", "https://commons.wikimedia.org/wiki/File:Carina_Nebula.jpg"),
        "lmc" to sample(R.drawable.target_lmc, "ESA/NASA/JPL-Caltech/STScI (public domain)", "https://commons.wikimedia.org/wiki/File:Large_Magellanic_Cloud.jpg"),
        "smc" to sample(R.drawable.target_smc, "Martin Bernardi (CC BY-SA 4.0)", "https://commons.wikimedia.org/wiki/File:Small_Magellanic_Cloud.jpg"),
        "ngc5139" to sample(R.drawable.target_ngc5139, "ESO (CC BY 4.0)", "https://commons.wikimedia.org/wiki/File:Omega_Centauri_by_ESO.jpg"),
        "m81" to sample(R.drawable.target_m81, "Taavi Niittee (CC0)", "https://commons.wikimedia.org/wiki/File:Bode_and_Cigar_galaxies.jpg"),
        "m51" to sample(R.drawable.target_m51, "NASA/ESA Hubble (public domain)", "https://commons.wikimedia.org/wiki/File:Messier51_sRGB.jpg"),
        "leo-triplet" to sample(R.drawable.target_leo_triplet, "Taavi Niittee (CC BY-SA 4.0)", "https://commons.wikimedia.org/wiki/File:Leo_triplet_t%C3%B5rva.jpg"),
        "m101" to sample(R.drawable.target_m101, "NASA, ESA and the Hubble Heritage Team (public domain)", "https://commons.wikimedia.org/wiki/File:A_Detailed_Look_at_Spiral_Galaxy_M101_(2006-10-1866).jpg"),
        "m33" to sample(R.drawable.target_m33, "Taavi Niittee (CC0)", "https://commons.wikimedia.org/wiki/File:Kolmnurga_galaktika_t%C3%B5rva.jpg"),
        "m104" to sample(R.drawable.target_m104, "NASA/ESA Hubble (public domain)", "https://commons.wikimedia.org/wiki/File:M104_ngc4594_sombrero_galaxy_hi-res.jpg"),
        "ngc5128" to sample(R.drawable.target_ngc5128, "ESO (CC BY 4.0)", "https://commons.wikimedia.org/wiki/File:Centaurus_A.jpg"),
        "ngc253" to sample(R.drawable.target_ngc253, "Dylan O'Donnell (CC0)", "https://commons.wikimedia.org/wiki/File:NGC_253_Caldwell_65_The_Sculptor_Galaxy.jpg"),
        "ngc6992" to sample(R.drawable.target_ngc6992, "Andy Weeks (public domain)", "https://commons.wikimedia.org/wiki/File:The_Eastern_Veil_Nebula_-_Flickr_-_astrophotography_andy.jpg"),
        "ngc2237" to sample(R.drawable.target_ngc2237, "Sebas FL (CC BY-SA 4.0)", "https://commons.wikimedia.org/wiki/File:Rosette_Nebula_(NGC_2237,_NGC_2238,_NGC_2239,_NGC_2249,_Sh2-275,_Caldwell_49).png"),
        "ic434" to sample(R.drawable.target_ic434, "Ivan Bok (CC BY 4.0)", "https://commons.wikimedia.org/wiki/File:The_Horsehead_and_Flame_Nebula.jpg"),
        "ngc1499" to sample(R.drawable.target_ngc1499, "Taavi Niittee (CC BY 4.0)", "https://commons.wikimedia.org/wiki/File:California_nebula_from_Estonia.jpg"),
        "ic1805" to sample(R.drawable.target_ic1805, "NASA/JPL-Caltech/UCLA (public domain)", "https://commons.wikimedia.org/wiki/File:Heart_and_Soul_nebulae.jpg"),
        "m16" to sample(R.drawable.target_m16, "NASA, ESA and the Hubble Heritage Team (public domain)", "https://commons.wikimedia.org/wiki/File:Pillars_of_creation_2014_HST_WFC3-UVIS_full-res.jpg"),
        "m27" to sample(R.drawable.target_m27, "Bill Schoening / NOIRLab (CC BY 4.0)", "https://commons.wikimedia.org/wiki/File:M27,_NGC_6853,_Dumbbell_Nebula_(noao-02674).jpg"),
        "m44" to sample(R.drawable.target_m44, "Taavi Niittee (CC0)", "https://commons.wikimedia.org/wiki/File:S%C3%B5im.jpg"),
        "ngc104" to sample(R.drawable.target_ngc104, "ESO (CC BY 3.0)", "https://commons.wikimedia.org/wiki/File:47_Tucanae.jpg"),
        "ngc4755" to sample(R.drawable.target_ngc4755, "NOIRLab/NSF/AURA (CC BY 4.0)", "https://commons.wikimedia.org/wiki/File:The_%22Jewel_Box%22,_NGC_4755_(noao-6053).jpg"),
        "rho-oph" to sample(R.drawable.target_rho_oph, "Dylan O'Donnell (CC0)", "https://commons.wikimedia.org/wiki/File:Antares_and_the_Rho_Ophiuchus_Region.jpg"),
        "scorpius" to sample(R.drawable.target_scorpius, "cafuego (CC BY-SA 2.0)", "https://commons.wikimedia.org/wiki/File:Scorpius_-_Flickr_-_cafuego.jpg"),
        "crux" to sample(R.drawable.target_crux, "ESO/Y. Beletsky (CC BY 4.0)", "https://commons.wikimedia.org/wiki/File:A_kite_in_the_Southern_Sky_(yb_southerncross_cc).jpg"),
        "leo" to sample(R.drawable.target_leo, "Thomas Bresson (CC BY 3.0)", "https://commons.wikimedia.org/wiki/File:2012-03-24_21-15-43-lion-5fov-9f-5s-iso640-72d-27o.jpg"),
        "moon-closeup" to sample(R.drawable.target_moon_closeup, "NASA (public domain)", "https://commons.wikimedia.org/wiki/File:Along_the_Lunar_Terminator_(art002e014045).jpg"),
        "moonlit-landscape" to sample(R.drawable.target_moonlit_landscape, "Giuseppe Milo (CC BY 3.0)", "https://commons.wikimedia.org/wiki/File:Moonlight_Florida_United_States_Travel_Photography_(233161441).jpeg"),
    )
}

/** Small photo on the collapsed suggestion row. Replaces the kind icon when a sample exists. */
@Composable
fun TargetSampleThumb(sample: TargetSample, name: String, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(sample.drawable),
        contentDescription = stringResource(R.string.target_sample_photo, name),
        modifier = modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
        contentScale = ContentScale.Crop,
        colorFilter = samplePhotoFilter(),
    )
}

/** Larger photo and the credit, shown when the suggestion row is open. The thumbnail already names the target. */
@Composable
fun TargetSampleDetail(sample: TargetSample, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier.fillMaxWidth()) {
        Image(
            painter = painterResource(sample.drawable),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(156.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop,
            colorFilter = samplePhotoFilter(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.target_sample_credit, sample.credit),
            modifier = Modifier.clickable(
                onClickLabel = stringResource(R.string.target_sample_open_credit),
            ) { openSampleSource(context, sample.sourceUrl) },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Luminance into red. Green and blue stay at zero, matching [NightPalette.NightVision]. */
@Composable
private fun samplePhotoFilter(): ColorFilter? {
    if (NightColors.palette != NightPalette.NightVision) return null
    return nightVisionPhoto
}

private val nightVisionPhoto = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            0.213f, 0.715f, 0.072f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

/** No browser leaves the credit on screen. The photo itself does not depend on the link. */
internal fun openSampleSource(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // The credit text is already visible.
    }
}
