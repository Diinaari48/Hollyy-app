package com.example.util

import com.example.data.model.Item

object SampleData {
    val sampleMedicines: List<Item> = listOf(
        Item(
            name = "Paracetamol 500mg Tabs",
            systemName = "PARA-TAB-500",
            cost = 0.50,
            wholesalePrice = 0.80,
            price = 1.00
        ),
        Item(
            name = "Amoxicillin 500mg Caps",
            systemName = "AMOX-CAP-500",
            cost = 1.20,
            wholesalePrice = 1.80,
            price = 2.50
        ),
        Item(
            name = "Ibuprofen 400mg Tabs",
            systemName = "IBU-TAB-400",
            cost = 0.75,
            wholesalePrice = 1.10,
            price = 1.50
        ),
        Item(
            name = "Omeprazole 20mg Caps",
            systemName = "OME-CAP-20",
            cost = 1.50,
            wholesalePrice = 2.20,
            price = 3.00
        ),
        Item(
            name = "Ciprofloxacin 500mg Tabs",
            systemName = "CIPRO-TAB-500",
            cost = 1.80,
            wholesalePrice = 2.50,
            price = 3.50
        ),
        Item(
            name = "Metformin 500mg Tabs",
            systemName = "MET-TAB-500",
            cost = 0.90,
            wholesalePrice = 1.40,
            price = 2.00
        ),
        Item(
            name = "Azithromycin 500mg Tabs (Pack 3)",
            systemName = "AZITH-TAB-500",
            cost = 2.20,
            wholesalePrice = 3.20,
            price = 4.50
        ),
        Item(
            name = "Cetirizine 10mg Tabs",
            systemName = "CET-TAB-10",
            cost = 0.40,
            wholesalePrice = 0.70,
            price = 1.00
        ),
        Item(
            name = "Artemether Lumefantrine (Coartem)",
            systemName = "ACT-COARTEM-24",
            cost = 2.80,
            wholesalePrice = 3.90,
            price = 5.00
        ),
        Item(
            name = "Oral Rehydration Salts (ORS)",
            systemName = "ORS-SACHET",
            cost = 0.20,
            wholesalePrice = 0.35,
            price = 0.50
        ),
        Item(
            name = "Salbutamol Inhaler 100mcg",
            systemName = "SALB-INH-100",
            cost = 2.50,
            wholesalePrice = 3.60,
            price = 5.00
        ),
        Item(
            name = "Diclofenac 50mg Tabs",
            systemName = "DICL-TAB-50",
            cost = 0.60,
            wholesalePrice = 0.90,
            price = 1.25
        )
    )

    const val SAMPLE_CSV = """Item,cost,Magaca systemka,macamil,qiimaha
Paracetamol 500mg,0.50,PARA-500,0.80,1.00
Amoxicillin 500mg,1.20,AMOX-500,1.80,2.50
Ibuprofen 400mg,0.75,IBU-400,1.10,1.50
Omeprazole 20mg,1.50,OMEP-20,2.20,3.00
Ciprofloxacin 500mg,1.80,CIPR-500,2.50,3.50
Metformin 500mg,0.90,MET-500,1.40,2.00
Azithromycin 500mg,2.20,AZITH-500,3.20,4.50
Cetirizine 10mg,0.40,CET-10,0.70,1.00
Coartem 20/120,2.80,ACT-COAR,3.90,5.00
ORS Sachet,0.20,ORS-1,0.35,0.50"""
}
