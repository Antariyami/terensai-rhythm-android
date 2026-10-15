package kz.terensai.rhythm.checklists

import java.util.UUID

/** Domain snapshots are independent of programme timelines and personal work. */
data class ChecklistItem(
    val id:String,
    val title:String,
    val quantity:Int?=null,
    val unit:String?=null
)
data class ChecklistBlock(val id:String,val title:String,val items:List<ChecklistItem>)
data class ChecklistTemplate(
    val id:String,
    val type:String,
    val title:String,
    val version:Int,
    val blocks:List<ChecklistBlock>
) {
    val totalItems:Int get()=blocks.sumOf{it.items.size}
    fun validated():ChecklistTemplate {
        require(type in setOf("BAG","RECEPTION","PREPARATION","HANDOVER"))
        require(id.length in 1..120 && title.trim().length in 2..120)
        require(version>=1 && blocks.size in 1..30)
        require(totalItems in 1..250)
        val ids=mutableSetOf<String>()
        blocks.forEach { block->
            require(block.id.length in 1..120 && ids.add(block.id))
            require(block.title.trim().length in 1..120 && block.items.size<=150)
            block.items.forEach{ item->
                require(item.id.length in 1..120 && ids.add(item.id))
                require(item.title.trim().length in 1..250)
                require(item.quantity==null || item.quantity in 1..10000)
                require(item.unit==null || item.unit.length<=30)
            }
        }
        return this
    }
}

object ChecklistEditing {
    fun moveBlock(template:ChecklistTemplate,index:Int,delta:Int):ChecklistTemplate {
        val dest=index+delta
        if(index !in template.blocks.indices || dest !in template.blocks.indices)return template
        val copy=template.blocks.toMutableList()
        val item=copy.removeAt(index);copy.add(dest,item)
        return template.copy(blocks=copy)
    }
    fun moveItem(template:ChecklistTemplate,blockIndex:Int,index:Int,delta:Int):ChecklistTemplate {
        val block=template.blocks.getOrNull(blockIndex)?:return template
        val dest=index+delta
        if(index !in block.items.indices || dest !in block.items.indices)return template
        val copy=block.items.toMutableList()
        val item=copy.removeAt(index);copy.add(dest,item)
        return template.copy(blocks=template.blocks.toMutableList().also{
            it[blockIndex]=block.copy(items=copy)
        })
    }
    fun addBlock(template:ChecklistTemplate,title:String="Новый блок"):ChecklistTemplate =
        template.copy(blocks=template.blocks+ChecklistBlock(UUID.randomUUID().toString(),title,listOf(
            ChecklistItem(UUID.randomUUID().toString(),"Новый пункт")
        )))
    fun addItem(template:ChecklistTemplate,blockIndex:Int):ChecklistTemplate {
        val block=template.blocks.getOrNull(blockIndex)?:return template
        return template.copy(blocks=template.blocks.toMutableList().also {
            it[blockIndex]=block.copy(items=block.items+
                ChecklistItem(UUID.randomUUID().toString(),"Новый пункт"))
        })
    }
    fun removeBlock(template:ChecklistTemplate,index:Int):ChecklistTemplate {
        if(index !in template.blocks.indices || template.blocks.size<2)return template
        return template.copy(blocks=template.blocks.filterIndexed{idx,_->idx!=index})
    }
    fun removeItem(template:ChecklistTemplate,blockIndex:Int,index:Int):ChecklistTemplate {
        val block=template.blocks.getOrNull(blockIndex)?:return template
        if(index !in block.items.indices || template.totalItems<=1)return template
        return template.copy(blocks=template.blocks.toMutableList().also{
            it[blockIndex]=block.copy(items=block.items.filterIndexed{idx,_->idx!=index})
        })
    }
}
