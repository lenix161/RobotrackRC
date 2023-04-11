package com.example.robotrackrc.recyclerview

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.robotrackrc.R
import com.example.robotrackrc.databinding.BluetoothListItemBinding
import com.example.robotrackrc.datamodel.BluetoothDeviceItem

class BtDevicesListAdapter(private val listener: Listener)
    : ListAdapter<BluetoothDeviceItem, BtDevicesListAdapter.ItemHolder>(ItemComparator()) {

    class ItemHolder(view: View): RecyclerView.ViewHolder(view){
        private val binding = BluetoothListItemBinding.bind(view)

        fun setData(item: BluetoothDeviceItem, listener: Listener){
            binding.deviceName.text = item.deviceName
            binding.deviceMac.text = item.deviceMac
            itemView.setOnClickListener { listener.onClick(item) }
        }

        companion object{
            fun initialize(parent: ViewGroup): ItemHolder{
                return ItemHolder(LayoutInflater
                    .from(parent.context)
                    .inflate(R.layout.bluetooth_list_item, parent, false))
            }
        }
    }

    class ItemComparator: DiffUtil.ItemCallback<BluetoothDeviceItem>(){
        override fun areItemsTheSame(oldItem: BluetoothDeviceItem, newItem: BluetoothDeviceItem): Boolean {
            return oldItem.deviceMac == newItem.deviceMac
        }

        override fun areContentsTheSame(
            oldItem: BluetoothDeviceItem,
            newItem: BluetoothDeviceItem
        ): Boolean {
            return oldItem == newItem
        }

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemHolder {
        return ItemHolder.initialize(parent)
    }

    override fun onBindViewHolder(holder: ItemHolder, position: Int) {
        holder.setData(getItem(position), listener)
    }

    interface Listener{
        fun onClick(item: BluetoothDeviceItem)
    }
}