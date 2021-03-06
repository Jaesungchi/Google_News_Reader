package com.kotlin.jaesungchi.rss_news_reader.Model

import android.util.Log
import com.kotlin.jaesungchi.rss_news_reader.*
import com.kotlin.jaesungchi.rss_news_reader.InterFaces.ModelCallBacks
import com.kotlin.jaesungchi.rss_news_reader.util.SSLConnect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.IOException
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory

class NewsModel(val modelCallBack: ModelCallBacks){
    private var coroutineScope = CoroutineScope(Dispatchers.Default)
    //구글 Rss에 나온 링크에 접속하여 Link들만 파싱하는 메소드
    fun downloadData(){
        coroutineScope.launch {
            var url = URL(GOOGLE_RSS_URL)
            var dbf = DocumentBuilderFactory.newInstance()
            var db = dbf.newDocumentBuilder()
            var doc = db.parse(InputSource(url.openStream()))
            doc.documentElement.normalize()
            var itemNodeList = doc.getElementsByTagName(ITEM_WORD)

            for (index in 0 until itemNodeList.length) {
                val node = itemNodeList.item(index)
                val element = node as Element
                val link = element.getElementsByTagName(LINK_WORD).item(0).childNodes.item(0).nodeValue
                getNewsData(link)
            }
        }
    }

    //NewsData의 Link마다 접소가여 OpenGraph의 값을 파싱하는 메소드
    private fun getNewsData(link : String){
        coroutineScope.launch {
            try {
                var newNews = NewsDTO("", "", link, "", ArrayList())
                var ssl = SSLConnect() //인증서 에러 방지
                ssl.postHttps(link, 1000, 1000)
                var con = Jsoup.connect(link).ignoreContentType(true).timeout(1000)
                var statusCode = con.ignoreHttpErrors(true).execute().statusCode()
                if (statusCode.toString()[0] != '2') //2로시작하는 응답은 성공
                    return@launch
                var doc = con.get()
                newNews.title = doc.title()
                var ogTags = doc.select(OG_BASE_WORD)
                for (i in ogTags) {
                    when (i.attr(OG_PROPERTY_WORD)) {
                        OG_IMAGE_WORD -> newNews.imageLink = i.attr(OG_CONTENT_WORD)
                        OG_DESCRIPTION_WORD -> newNews.content = i.attr(OG_CONTENT_WORD)
                    }
                }
                if (newNews.title.isNullOrBlank() || newNews.content.isNullOrBlank())
                    return@launch
                newNews.tags = getKeywordinContent(newNews.content)
                CoroutineScope(Dispatchers.Main).launch {
                    modelCallBack.onModelUpdated(newNews) //MainThread에 업데이트를 한다
                }
            }catch (e : IOException) {
                Log.e("linkError", e.toString())
            }
        }
    }

    //Content를 통해 Tag를 추출하여 반환하는
    private fun getKeywordinContent(content : String) : ArrayList<String>{
        var regex = """[^\uAC00-\uD7A30-9a-zA-Z\s]""".toRegex()
        var arr = content.replace(regex," ").split(' ').map{ it.trim()}.sorted()
        var countArr = ArrayList<Int>()
        var wordArr = ArrayList<String>()
        var beforeWord = arr[0]
        var arrIndex = 0
        var countNow = 1
        for(index in 0 until arr.size) //오름차순 정렬 후 빈칸과 작은 글자는 넘어간다.
            if(!arr[index].isNullOrBlank() && arr[index].length > 1) {
                beforeWord = arr[index]
                arrIndex = index + 1// 다음글자부터 순환
                break
            }
        for(i in arrIndex until arr.size ){
            if(arr[i].length < 2) //글자가 작은경우 넘어간다.
                continue
            if(beforeWord == arr[i]){ //글자가 같은경우 카운트를 늘리고 다음글자.
                countNow++
                continue
            }
            //단어가 앞과 달라진 경우
            if(wordArr.size == 0){ //아무것도 없는 경우 추가.
                wordArr.add(beforeWord)
                countArr.add(countNow)
            }
            else { //있는경우 맨 뒤글자부터 비교하며 넘어간다.
                for (tempIndex in wordArr.size - 1 downTo 0) {
                    if (countNow <= countArr[tempIndex]) {
                        if (wordArr.size < 3) {
                            wordArr.add(beforeWord)
                            countArr.add(countNow)
                        }
                        break
                    }
                    if (tempIndex != 2) {
                        if (wordArr.size <= tempIndex + 1) {
                            wordArr.add(wordArr[tempIndex])
                            countArr.add(countArr[tempIndex])
                        } else {
                            wordArr[tempIndex + 1] = wordArr[tempIndex]
                            countArr[tempIndex + 1] = countArr[tempIndex]
                        }
                    }
                    wordArr[tempIndex] = beforeWord
                    countArr[tempIndex] = countNow
                }
            }
            beforeWord = arr[i]
            countNow = 1
        }
        return wordArr
    }
}