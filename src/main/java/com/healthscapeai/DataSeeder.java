package com.healthscapeai;

import org.slf4j.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.util.UUID;

/** Seeds Hyderabad localities (approximate centre coordinates) and the first official account. */
@Component
public class DataSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String[] AREAS = {
        "Charminar,Central,17.3616,78.4747","Abids,Central,17.3920,78.4750","Nampally,Central,17.3900,78.4700","Khairatabad,Central,17.4123,78.4624",
        "Somajiguda,Central,17.4239,78.4580","Ameerpet,Central,17.4375,78.4483","Begumpet,Central,17.4440,78.4676","Secunderabad,Central,17.4399,78.4983",
        "Musheerabad,Central,17.4170,78.4950","Banjara Hills,West,17.4156,78.4347","Jubilee Hills,West,17.4325,78.4071","Madhapur,West,17.4486,78.3908",
        "Hitec City,West,17.4435,78.3772","Gachibowli,West,17.4401,78.3489","Kondapur,West,17.4616,78.3649","Manikonda,West,17.4010,78.3870",
        "Mehdipatnam,West,17.3950,78.4396","Tolichowki,West,17.4009,78.4100","Attapur,South,17.3680,78.4285","Rajendranagar,South,17.3213,78.4050",
        "Shamshabad,South,17.2403,78.4294","Falaknuma,South,17.3320,78.4670","Chandrayangutta,South,17.3175,78.4840","Santoshnagar,South,17.3600,78.5000",
        "Saidabad,South,17.3600,78.5100","Dilsukhnagar,East,17.3688,78.5247","Kothapet,East,17.3680,78.5400","LB Nagar,East,17.3457,78.5522",
        "Hayathnagar,East,17.3260,78.6040","Nagole,East,17.3714,78.5678","Uppal,East,17.4058,78.5591","Habsiguda,East,17.4156,78.5427",
        "Tarnaka,East,17.4283,78.5330","Amberpet,East,17.3950,78.5170","Malkajgiri,North,17.4534,78.5266","Alwal,North,17.5000,78.5100",
        "Kompally,North,17.5400,78.4860","Bowenpally,North,17.4727,78.4800","Sanathnagar,North,17.4553,78.4414","Balanagar,North,17.4710,78.4440",
        "Moosapet,North,17.4640,78.4260","Kukatpally,North,17.4948,78.3996","Miyapur,North,17.4969,78.3548","Nizampet,North,17.5150,78.3800",
        "Bachupally,North,17.5440,78.3580","Patancheru,North,17.5330,78.2650"};

    private final VillageRepository villages; private final AppUserRepository users; private final PasswordEncoder enc;
    public DataSeeder(VillageRepository v, AppUserRepository u, PasswordEncoder e) { villages = v; users = u; enc = e; }

    @Override public void run(String... args) {
        if (villages.count() == 0)
            for (String a : AREAS) { var p = a.split(","); villages.save(new Village(p[0], p[1], Double.parseDouble(p[2]), Double.parseDouble(p[3]))); }
        if (users.count() == 0) {
            String user = System.getenv().getOrDefault("HS_ADMIN_USER", "official");
            String pass = System.getenv("HS_ADMIN_PASSWORD");
            if (pass == null || pass.isBlank()) {
                pass = UUID.randomUUID().toString().substring(0, 12);
                log.warn("Created official account '{}' with generated password: {}", user, pass);
            }
            users.save(new AppUser(user, enc.encode(pass), "ADMIN"));
        }
    }
}
