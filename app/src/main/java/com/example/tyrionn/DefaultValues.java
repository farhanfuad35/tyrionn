package com.example.tyrionn;

import java.util.HashMap;

public class DefaultValues {
    final String b1_baba = "01713031557";
    final String b2_amma = "01773657785";
    final String b3_niloy = "01763185363";
    final String b4_kaku = "01844484110";
    final String b5_panna = "01743325437";
    final String b6_zuli = "01743946285";


    final String b1Name = "Baba";
    final String b2Name = "Amma";
    final String b3Name = "Niloy";
    final String b4Name = "Kaku";
    final String b5Name = "Panna";
    final String b6Name = "Zuli";
    
    public HashMap<String, String> getDefaults(){
        HashMap<String, String> defaultMapping = new HashMap<>();
        defaultMapping.put("b1Title", b1Name);
        defaultMapping.put("b2Title", b2Name);
        defaultMapping.put("b3Title", b3Name);
        defaultMapping.put("b4Title", b4Name);
        defaultMapping.put("b5Title", b5Name);
        defaultMapping.put("b6Title", b6Name);

        defaultMapping.put("b1", b1_baba);
        defaultMapping.put("b2", b2_amma);
        defaultMapping.put("b3", b3_niloy);
        defaultMapping.put("b4", b4_kaku);
        defaultMapping.put("b5", b5_panna);
        defaultMapping.put("b6", b6_zuli);
        
        return defaultMapping;
    }
}
