package com.example.outfy

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.outfy.adapter.ProductAdapter
import com.example.outfy.model.Product

class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)

        val productList = listOf(
            Product("Knit Polo Shirt", "$120", R.drawable.logo_elem),
            Product("Pleated Midi Skirt", "$145", R.drawable.logo_img),
            Product("Leather Crossbody", "$210", R.drawable.splash_screen_image),
            Product("Classic Loafers", "$185", R.drawable.logo_elem)
        )

        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.adapter = ProductAdapter(productList)
    }
}