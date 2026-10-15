package kz.terensai.rhythm.checklists

import org.json.JSONArray
import org.json.JSONObject

/** Validated, versioned data exchange. No code execution or personal run data. */
object ChecklistJson {
    const val SCHEMA_VERSION=1
    const val MAX_BYTES=300_000

    fun encode(template:ChecklistTemplate):String {
        template.validated()
        return JSONObject().put("schemaVersion",SCHEMA_VERSION)
            .put("templateId",template.id).put("type",template.type)
            .put("title",template.title).put("templateVersion",template.version)
            .put("blocks",JSONArray().apply{
                template.blocks.forEach{block->
                    put(JSONObject().put("id",block.id).put("title",block.title)
                        .put("items",JSONArray().apply{
                            block.items.forEach{item->
                                put(JSONObject().put("id",item.id)
                                    .put("title",item.title)
                                    .put("quantity",item.quantity?:JSONObject.NULL)
                                    .put("unit",item.unit?:JSONObject.NULL))
                            }
                        }))
                }
            }).toString()
    }
    fun decode(json:String):ChecklistTemplate {
        require(json.toByteArray(Charsets.UTF_8).size<=MAX_BYTES){"Слишком большой файл шаблона"}
        val root=JSONObject(json)
        require(root.getInt("schemaVersion")==SCHEMA_VERSION){"Версия шаблона не поддерживается"}
        val blocks=root.getJSONArray("blocks")
        require(blocks.length() in 1..30)
        val value=ChecklistTemplate(
            id=root.getString("templateId"),
            type=root.getString("type"),
            title=root.getString("title"),
            version=root.getInt("templateVersion"),
            blocks=(0 until blocks.length()).map{i->
                val block=blocks.getJSONObject(i)
                val items=block.getJSONArray("items")
                require(items.length()<=150)
                ChecklistBlock(block.getString("id"),block.getString("title"),
                    (0 until items.length()).map{j->
                        val item=items.getJSONObject(j)
                        ChecklistItem(
                            item.getString("id"),item.getString("title"),
                            if(item.isNull("quantity")||!item.has("quantity"))null else item.getInt("quantity"),
                            if(item.isNull("unit")||!item.has("unit"))null else item.getString("unit")
                        )
                    })
            }
        )
        return value.validated()
    }

    fun encodeAnswers(ids:Set<String>)=JSONArray(ids.sorted()).toString()
    fun decodeAnswers(json:String):Set<String> {
        val arr=JSONArray(json)
        require(arr.length()<=250)
        return (0 until arr.length()).map{arr.getString(it)}.toSet()
    }
    fun encodeNotes(notes:Map<String,String>)=JSONObject().apply{
        notes.forEach{(key,value)->put(key,value.take(2000))}
    }.toString()
    fun decodeNotes(json:String):Map<String,String> {
        val obj=JSONObject(json)
        return obj.keys().asSequence().associateWith{obj.getString(it)}
    }
}
