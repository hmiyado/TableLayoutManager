package com.github.hmiyado.tablelayoutmanager

import android.support.v7.widget.RecyclerView
import android.view.ViewGroup
import android.widget.TextView

class Adapter : RecyclerView.Adapter<Adapter.ViewHolder>() {
    private val map = ("Lorem ipsum dolor sit amet, consectetur adipiscing elit. Etiam hendrerit auctor elit quis lobortis. Aliquam finibus posuere ligula, id finibus elit efficitur nec. Aenean nec placerat justo, id euismod purus. Nam libero neque, consectetur in mauris vel, mollis ornare leo. Mauris bibendum metus sit amet neque sollicitudin, at auctor mauris mollis. Morbi ultrices tellus sit amet lobortis scelerisque. In posuere, nibh non elementum mattis, turpis lectus porta mauris, eu maximus velit leo non dolor. Fusce commodo, nulla ac rhoncus commodo, ex augue lobortis augue, in feugiat lacus ligula euismod risus. Nunc eu pellentesque nisi. Quisque pulvinar malesuada metus, a pharetra purus accumsan sit amet. Nunc eu tincidunt libero, quis gravida justo. Duis finibus vehicula nisi eu sodales.\n" +
            "\n" +
            "Nulla lacinia, risus in consectetur sollicitudin, libero velit rhoncus ex, a efficitur est odio non leo. Mauris tempor orci auctor mi tincidunt, id aliquam leo porttitor. Vivamus eget quam nec arcu auctor efficitur sed ut leo. Ut et justo bibendum, luctus eros sit amet, sodales leo. Donec enim urna, lacinia nec sodales eget, porta vitae dui. Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas. Etiam vulputate massa et arcu euismod venenatis. Proin mauris enim, sagittis ac viverra eget, mollis gravida mauris. Suspendisse in mollis mauris. Cras eget tortor eget dui sollicitudin aliquam. Morbi rhoncus erat magna, id vulputate nisl gravida ac. Integer cursus aliquet massa non sagittis. Sed iaculis, tellus quis tristique accumsan, erat ligula convallis neque, et interdum eros risus vitae felis.\n" +
            "\n" +
            "Donec sit amet nibh et lorem malesuada mollis. Nullam eget commodo purus, ut imperdiet tellus. Suspendisse fermentum molestie turpis eu volutpat. Ut congue neque eget diam imperdiet, eu lobortis augue aliquet. Morbi consequat massa in felis maximus lacinia. Duis viverra, massa nec congue molestie, velit dui consectetur arcu, ac porta ipsum nunc interdum massa. Pellentesque nunc velit, aliquam nec mollis eu, congue pretium ligula. Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere cubilia Curae; Suspendisse potenti. Suspendisse nunc erat, rutrum vel volutpat at, interdum nec mi. Donec molestie congue magna, sed tempor massa ornare at.\n" +
            "\n" +
            "Ut posuere ligula sit amet interdum accumsan. Duis iaculis urna id faucibus varius. Mauris gravida magna nec metus rhoncus eleifend. Sed et tortor eros. In venenatis sodales est, id euismod ante posuere ut. Fusce ipsum ipsum, tincidunt in justo id, porta sagittis orci. Etiam sapien lorem, hendrerit at gravida eget, rhoncus ut eros. In sit amet euismod leo. Mauris fermentum consectetur commodo. Vivamus elementum, neque et porta interdum, risus nibh aliquet mauris, eu pretium metus nunc ac elit.\n" +
            "\n" +
            "Quisque eget varius sem. Aenean nec turpis quis libero volutpat tristique luctus at nibh. Etiam eros orci, eleifend et sem convallis, viverra tristique ante. Aliquam gravida, eros non fringilla condimentum, est nisi efficitur enim, semper venenatis turpis est at sem. Praesent gravida rhoncus nibh, ac elementum arcu consectetur id. Vivamus tincidunt et ipsum ac pretium. Aliquam commodo a neque vitae viverra.")
            .lines()
            .mapIndexed { index, s -> "paragraph $index" to s.split(' ') }
            .toMap()

    val row: Int
        get() {
            return map.mapValues { (_, v) -> v.size }.maxBy { (_, v) -> v }?.value ?: 0
        }

    val column: Int
        get() = map.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(TextView(parent.context))
    }

    override fun getItemCount(): Int {
        return row * column
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val currentRow = position % row
        val currentColumn = position / column
        holder.text = try {
            map.toList()[currentColumn].second.getOrNull(currentRow) ?: "blank"
        } catch (e: IndexOutOfBoundsException) {
            "blank"
        } + "($currentRow, $currentColumn)"
    }


    class ViewHolder(private val view: TextView) : RecyclerView.ViewHolder(view) {
        var text: CharSequence
            get() = view.text
            set(value) {
                view.text = value
            }
    }
}