package com.arron.mydemo.kotlin

class KotlinDemo {
    companion object{
        @JvmStatic
        fun main(args: Array<String>) {
            System.out.println(length.transform("2"))
        }
    }



}

fun interface Transformer<T,U>{
    fun transform(x:T):U
}
val length = Transformer {
        x: String -> x.length
}
