$(function() {
    /***
     * 动态打字效果
     */
    let divTyping = $('#article-title')
    let i = 0,timer = 0,str = divTyping.text()
    function typing () {
        if (i <= str.length) {
            divTyping.text(str.slice(0, i++))
            timer = setTimeout(typing, 100)
        } else {
            clearTimeout(timer)
        }
    }
    typing()
})