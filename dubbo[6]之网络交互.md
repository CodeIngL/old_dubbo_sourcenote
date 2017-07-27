## dubbo网络交互

上一篇我们详细介绍了dubbo是如何暴露网络服务，这一篇我们将对dubbo的网络通信交互细节作出探究。

        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            public ChannelPipeline getPipeline() {
                //netty编码适配器
                NettyCodecAdapter adapter = new NettyCodecAdapter(getCodec(), getUrl(), NettyServer.this);
                //设置pipeline
                ChannelPipeline pipeline = Channels.pipeline();
                pipeline.addLast("decoder", adapter.getDecoder());
                pipeline.addLast("encoder", adapter.getEncoder());
                pipeline.addLast("handler", nettyHandler);
                return pipeline;
            }
        });

以上代码，在上一篇文章doOpen方法中的片段。值得说明的是，默认使用netty来描述的，对于netty熟悉的童鞋应该很熟悉了，这是模板代码。

### NettyCodecAdapter
---

这个是代码中已经说明，是编码解码器的适配。我们可以看出pipeline总共被设置了三个ChannelHandler。其中NettyCodecAdapter内部携带了两个Handler，这2个Handler分别对应解码和编码。我们将详细展开。

tip:这里的ChannelHandler是netty中的概念，dubbo中也存在同样的概念

#### InternalDecoder
---

该类代表了解码handler（adapter.getDecoder())

其继承了netty框架中中的上行处理类(SimpleChannelUpstreamHandler)，值得注意的该类一通道一实例(没有@Sharable注解)，避免了并发问题。

该类在解码上做了很多事情，包括：协议解析，拆包粘包，对象的序列化。

	@Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent event) throws Exception {

			/**检测上一个channel传递下来的对象的合法性
			**/

            com.alibaba.dubbo.remoting.buffer.ChannelBuffer message;

            //本地buffer中有数据
            if (buffer.readable()) {
                //buffer是DynamicChannelBuffer类型
                //直接写入追加,一个协议包需要至少跨越3三个TCP包承载后发生
                if (buffer instanceof DynamicChannelBuffer) {
                    buffer.writeBytes(input.toByteBuffer());
                    message = buffer;
                } else {
                    //将普通的message实现转化为DynamicChannelBuffer实现，实现处理tcp拆包现象

                    //获得总共大小
                    int size = buffer.readableBytes() + input.readableBytes();
                    //创建DynamicChannelBuffer
                    message = com.alibaba.dubbo.remoting.buffer.ChannelBuffers.dynamicBuffer(
                        size > bufferSize ? size : bufferSize);
                    //将buffer中的东西写入message
                    message.writeBytes(buffer, buffer.readableBytes());
                    //将input中的东西写入message
                    message.writeBytes(input.toByteBuffer());
                }
            } else {
                //是一个数据包开始，因为没有buffer
                //包装netty的buffer为dubbo内部统一的结构处理
                message = com.alibaba.dubbo.remoting.buffer.ChannelBuffers.wrappedBuffer(
                    input.toByteBuffer());
            }

			//见第5点
            NettyChannel channel = NettyChannel.getOrAddChannel(ctx.getChannel(), url, handler);

            Object msg;
            int saveReaderIndex;
            try {
                // decode object.
                do {
                    //buffer有效的可读位置
                    saveReaderIndex = message.readerIndex();
                    try {
                        //通过传递包装的参数（nettyChannel和ChannelBuffers的实现类）
                        // 尝试解码TCP包中的数据，从而获得对象
                        msg = codec.decode(channel, message);
                    } catch (IOException e) {
                        //出错的处理
                        buffer = com.alibaba.dubbo.remoting.buffer.ChannelBuffers.EMPTY_BUFFER;
                        throw e;
                    }
                    //解码或获得对象是NEED_MORE_INPUT，也就是需要获得更多数据包完成数据的处理
                    if (msg == Codec2.DecodeResult.NEED_MORE_INPUT) {
                        //重置读位置的光标
                        message.readerIndex(saveReaderIndex);
                        break;
                    } else {
                        //校验，先前的读位置的光标和解码之后光标的位置
                        if (saveReaderIndex == message.readerIndex()) {
                            buffer = com.alibaba.dubbo.remoting.buffer.ChannelBuffers.EMPTY_BUFFER;
                            throw new IOException("Decode without read data.");
                        }
                        if (msg != null) {
                            //传递事件
                            Channels.fireMessageReceived(ctx, msg, event.getRemoteAddress());
                        }
                    }
                } while (message.readable());//下一个可用数据
            } finally {
                if (message.readable()) {
                    message.discardReadBytes();
                    buffer = message;
                } else {
                    buffer = com.alibaba.dubbo.remoting.buffer.ChannelBuffers.EMPTY_BUFFER;
                }
                NettyChannel.removeChannelIfDisconnected(ctx.getChannel());
            }
        }
以上就是关键通讯时，处理上行消息时的方法源码，不清楚的童鞋可以简单的学一下netty。
代码很长，做的事情也很多，我们慢慢道来。

3. 创建dubbo框架抽象通道缓冲区：message

4. 对实例数据缓存buffer进行处理，该结构是为了保存发生粘包拆包现象的TCP数据。
	1. 缓存中有数据：
		1. buffer是DynamicChannelBuffer类型处理
			1. DynamicChannelBuffer类型下，直接将netty字节缓存区写入缓存buffer中，将buffer赋值给message
				- 是dubbo协议，但至少跨越三个TCP包承载后发生
				- 或者是telnet协议处理
		2. buffer是其他ChannelBuffer类型处理
			1. 获得缓存buffer和netty缓冲区总共可读字节多少size
			2. 构建message为DynamicChannelBuffer类型(容量由size和配置buffersize决定)，并读入缓存buffer和netty缓冲区中可读的数据
	
	2. 缓存中无数据，根据处理netty的字节缓冲区的不同情况构建message为不同抽象实例
		1. netty字节缓冲区无效，返回空HeapChannelBuffer(EMPTY_BUFFER)
		2. netty字节缓冲区支持随机读写，返回底层byte数组包装的HeapChannelBuffer
		3. netty字节缓冲区不含byte数组(直接内存)，返回ByteBufferBackedChannelBuffer

5. 尝试从缓存中获得nettyChannel，并发安全，对于缓存操作，有则取，无则存
	1. nettyChannel吃用netty的上下文channel，url和nettyServer这个handler

6. 对message进行循环处理
	
	1. 保存message的起始读偏移量(用于回滚和重置)

	2. **使用属性codec对应类型进行解码，返回Object类型实例msg**
		1. 出错，就将buffer置为EMPTY_BUFFER，并扔出异常

	3. 对于msg为NEED_MORE_INPUT(还需要读取数据)，进行重置起始读偏移量，并跳出循环，进入第7点

	4. 对应类型的msg，传递事件给下一个handler处理
		1. 通过检查起始读位置和现在的读位置来检查是否读取了数据，无则就将buffer置为EMPTY_BUFFER，并扔出异常
	
7. final处理，完成扫尾处理
	1. message可读，缓冲区重置后，直接付给buffer(未读数据还有用)
	2. message不可读，buffer置为EMPTY_BUFFER
	3. 尝试删除netty的上下文channel，如果断开了连接

解码的过程如上所述，但我们依旧没见到细节的处理:协议处理，序列化处理，粘包拆包。不可否认的是这肯定已经发生，我们将会娓娓道来。读者可以发现上面有加粗的地方，也就是6.2，使用属性codec进行解码，默认情况下是用DubboCountCodec来处理。默认如何选择请看我的dubbo杂文一文

#### DubboCountCodec.decode
---
该解码器默认情况下就是上面持有codec属性了，其功能是为了尽量尝试解码出多个消息

    public Object decode(Channel channel, ChannelBuffer buffer) throws IOException {

        int save = buffer.readerIndex();

        MultiMessage result = MultiMessage.create();
        do {
            Object obj = codec.decode(channel, buffer);
            if (Codec2.DecodeResult.NEED_MORE_INPUT == obj) {
                buffer.readerIndex(save);
                break;
            } else {
                result.addMessage(obj);
                logMessageLength(obj, buffer.readerIndex() - save);
                save = buffer.readerIndex();
            }
        } while (true);
        if (result.isEmpty()) {
            return Codec2.DecodeResult.NEED_MORE_INPUT;
        }
        if (result.size() == 1) {
            return result.get(0);
        }
        return result;
    }

代码不多，也不是很长，我们介绍下整个逻辑	。

1. 第一步总是保存当前的读偏移量save，用于更新

2. 创建多个消息的载体结构result，该类总是尝试去解析多个数据包

3. 大循环
	1. **委托给被代理对象codec进行解码**
	
	2. 对解码返回值obj进行处理
		- 解码对象是NEED_MORE_INPUT，重置上一个save位置
		- 解码是其他对象
			- 加入消息载体result
			- 对序列化对象的内部数据，添加属性键值对说明（key由实际类型决定和value为包长）
		- 更新读偏移量save
4. 对循环处理后得到的消息载体的处理
	1. 内部没有数据，返回解码对象NEED_MORE_INPUT
	2. 只有一个对象，返回该对象
	3. 内部包含多个对象，返回该消息载体

逻辑基本上这样，细心读者应该能发现新的知识点:序列化对象(慢慢道来)。继续关注加粗的部分codec的处理，这个codec在这里是DubboCodec


#### ExchangeCodec.decode
---

我们提到codeC是DubboCodec，其主要功能是为了解码单个协议包，实际上在该类中找不到decode函数，默认使用了父类decode方法，也就是**ExchangeCodec.decode**方法

    public Object decode(Channel channel, ChannelBuffer buffer) throws IOException {
        int readable = buffer.readableBytes();
        byte[] header = new byte[Math.min(readable, HEADER_LENGTH)];
        buffer.readBytes(header);
        return decode(channel, buffer, readable, header);
    }
以上就是方法的实现代码，只有4行，直接解释

1. 获得buffer中可读的数据量readable
2. 尝试构建头部，为什么使用尝试来描述
	- dubbo协议头是了16个字节，当然很有可能可读的数量还少于16个字节(telnet协议），因而使用readable和HEADER_LENGTH中的较小值来构建长度
3. 尝试读取头部
4. 委托给其他方法继续处理

#### ExchangeCodec.decode(channel, buffer, readable, header)
---

该方法就是上述所说的委托方法，代码示意如下：

	protected Object decode(Channel channel, ChannelBuffer buffer, int readable, byte[] header) throws IOException {
        if (readable > 0 && header[0] != MAGIC_HIGH
                || readable > 1 && header[1] != MAGIC_LOW) {
            int length = header.length;
            if (header.length < readable) {
                header = Bytes.copyOf(header, readable);
                buffer.readBytes(header, length, readable - length);
            }

            for (int i = 1; i < header.length - 1; i ++) {
                if (header[i] == MAGIC_HIGH && header[i + 1] == MAGIC_LOW) {
                    buffer.readerIndex(buffer.readerIndex() - header.length + i);
                    header = Bytes.copyOf(header, i);
                    break;
                }
            }
            return super.decode(channel, buffer, readable, header);
        }

        if (readable < HEADER_LENGTH) {
            return DecodeResult.NEED_MORE_INPUT;
        }

        int len = Bytes.bytes2int(header, 12);
        checkPayload(channel, len);

        int tt = len + HEADER_LENGTH;
        if( readable < tt ) {
            return DecodeResult.NEED_MORE_INPUT;
        }

        ChannelBufferInputStream is = new ChannelBufferInputStream(buffer, len);

        try {
            return decodeBody(channel, is, header);
        } finally {
            if (is.available() > 0) {
                try {
                    if (logger.isWarnEnabled()) {
                        logger.warn("Skip input stream " + is.available());
                    }
                    StreamUtils.skipUnusedStream(is);
                } catch (IOException e) {
                    logger.warn(e.getMessage(), e);
                }
            }
        }
    }
上面的代码算很长了。我们还是慢慢解释这里面的设计


### 数据的解析

----------
该小点将会围绕上面的代码进行展开，并进行深入的分析，在这个方法调用里面会进行复杂的处理。

首先自然是判断协议包了，在dubbo框架中，其自定义协议包总是以2个长度的字节（魔数）来代表这是dubbo协议包
因而在这个方法中首先验证的就是这个。含魔数的原因很简单，但是不含魔数的原因却各种各样(可能是数据包出现问题，可能是TCP粘包拆包，也可能是dubbo支持的telnet造成的)。至于魔数的概念，不懂的小伙伴需要补充下知识。

### 不含魔数的处理

----------
上面我们说到，网络读取来的当前数据缓冲中可能不存在魔数，当然我们是要处理的。
	1. 尝试将buffer中的内容全部读入header
		- 之前说明header是为了尝试读取头部，既然现在不符合头部，那么久尽量将buffer中的数据取出来先
	2. 尝试寻找下一个魔数的坐标，为什么是尝试呢，这里无法区分是TCP拆包粘包还是telnet协议
		- 找到后，对buffer的起始读位置设定到下一个魔数的开始位置
		- 并将之前的数据构建为byte数组进行处理(header代表起始位置到下一个魔数的位置，这一段长度包含了数据因此需要处理
	3. 使用TelnetCodec进行处理，意味着是针对telnet协议进行处理，那么是dubbo协议包怎么样呢，我们慢慢道来


### telnet协议处理

	 protected Object decode(Channel channel, ChannelBuffer buffer, int readable, byte[] message) throws IOException {
        if (isClientSide(channel)) {
            return toString(message, getCharset(channel));
        }
        checkPayload(channel, readable);

        if (message == null || message.length == 0) {
            return DecodeResult.NEED_MORE_INPUT;
        }

        if (message[message.length - 1] == '\b') { // Windows backspace echo
            try {
                boolean doublechar = message.length >= 3 && message[message.length - 3] < 0; // double byte char
                channel.send(new String(doublechar ? new byte[] {32, 32, 8, 8} : new byte[] {32, 8}, getCharset(channel).name()));
            } catch (RemotingException e) {
                throw new IOException(StringUtils.toString(e));
            }
            return DecodeResult.NEED_MORE_INPUT;
        }

        for (Object command : EXIT) {
            if (isEquals(message, (byte[]) command)) {
                if (logger.isInfoEnabled()) {
                    logger.info(new Exception("Close channel " + channel + " on exit command: " + Arrays.toString((byte[])command)));
                }
                channel.close();
                return null;
            }
        }

        boolean up = endsWith(message, UP);
        boolean down = endsWith(message, DOWN);
        if (up || down) {
            LinkedList<String> history = (LinkedList<String>) channel.getAttribute(HISTORY_LIST_KEY);
            if (history == null || history.size() == 0) {
                return DecodeResult.NEED_MORE_INPUT;
            }
            Integer index = (Integer) channel.getAttribute(HISTORY_INDEX_KEY);
            Integer old = index;
            if (index == null) {
                index = history.size() - 1;
            } else {
                if (up) {
                    index = index - 1;
                    if (index < 0) {
                        index = history.size() - 1;
                    }
                } else {
                    index = index + 1;
                    if (index > history.size() - 1) {
                        index = 0;
                    }
                }
            }
            if (old == null || ! old.equals(index)) {
                channel.setAttribute(HISTORY_INDEX_KEY, index);
                String value = history.get(index);
                if (old != null && old >= 0 && old < history.size()) {
                    String ov = history.get(old);
                    StringBuilder buf = new StringBuilder();
                    for (int i = 0; i < ov.length(); i ++) {
                        buf.append("\b");
                    }
                    for (int i = 0; i < ov.length(); i ++) {
                        buf.append(" ");
                    }
                    for (int i = 0; i < ov.length(); i ++) {
                        buf.append("\b");
                    }
                    value = buf.toString() + value;
                }
                try {
                    channel.send(value);
                } catch (RemotingException e) {
                    throw new IOException(StringUtils.toString(e));
                }
            }
            return DecodeResult.NEED_MORE_INPUT;
        }
        for (Object command : EXIT) {
            if (isEquals(message, (byte[]) command)) {
                if (logger.isInfoEnabled()) {
                    logger.info(new Exception("Close channel " + channel + " on exit command " + command));
                }
                channel.close();
                return null;
            }
        }
        byte[] enter = null;
        for (Object command : ENTER) {
            if (endsWith(message, (byte[]) command)) {
                enter = (byte[]) command;
                break;
            }
        }
        if (enter == null) {
            return DecodeResult.NEED_MORE_INPUT;
        }
        LinkedList<String> history = (LinkedList<String>) channel.getAttribute(HISTORY_LIST_KEY);
        Integer index = (Integer) channel.getAttribute(HISTORY_INDEX_KEY);
        channel.removeAttribute(HISTORY_INDEX_KEY);
        if (history != null && history.size() > 0 && index != null && index >= 0 && index < history.size()) {
            String value = history.get(index);
            if (value != null) {
                byte[] b1 = value.getBytes();
                if (message != null && message.length > 0) {
                    byte[] b2 = new byte[b1.length + message.length];
                    System.arraycopy(b1, 0, b2, 0, b1.length);
                    System.arraycopy(message, 0, b2, b1.length, message.length);
                    message = b2;
                } else {
                    message = b1;
                }
            }
        }
        String result = toString(message, getCharset(channel));
        if (result != null && result.trim().length() > 0) {
            if (history == null) {
                history = new LinkedList<String>();
                channel.setAttribute(HISTORY_LIST_KEY, history);
            }
            if (history.size() == 0) {
                history.addLast(result);
            } else if (! result.equals(history.getLast())) {
                history.remove(result);
                history.addLast(result);
                if (history.size() > 10) {
                    history.removeFirst();
                }
            }
        }
        return result;
    }

代码很长，做的主要是支持telnet协议，但是对应我们来说，我们只需要关注一点，其返回值是什么，在又数据的情况下，其返回的字符文本串。


### 含有魔数的处理

----------

上面解析了不含魔数的处理，现在我们来探究下含有魔数的处理方式。

1. 检查缓冲区可读数据量和16相比，小于自然要直接返回NEED_MORE_INPUT，因为显然是数据不够。

2. 读取包头中的数据长度len(payload的长度)，头部第12个字节到第16个字节

3. 检查payload部分的数据大小是否符合要求(len不能大于我们对dubbo协议包数据部分的设定)

4. 构建整个dubbo协议包大小tt(head+payload)，比较缓冲区可读字节数量和整个协议包大小
	- 小于整个包长，自然要返回NEED_MORE_INPUT，因为显然是数据不够，发生拆包现象，需要网络继续读取

5. 包装缓冲区和payload长度为ChannelBufferInputStream实例is

6. 解码协议包payload部分，传入channel，is，header

### decodeBody的处理

----------
根据上文逻辑，数据包的payload部分就是由该函数处理的，搜索源码后，我们发现ExchangeCodec和DubboCodec都实现了该方法，当然之前由于是DubboCodec，我们自然需要关注这个，至于ExchangeCodec处理，我们后续会讨论。

#### DubboCodec.decodeBody(Channel channel, InputStream is, byte[] header)
---
这里我们以方法的签名做了标题。顺带说明下入参

1. 参数is代表了协议包输入流(一个dubbo协议包的payload部分)，以及持有了payload的长度部分
2. 参数header是协议包头
3. 参数channel不用多说

	protected Object decodeBody(Channel channel, InputStream is, byte[] header) throws IOException{

        byte flag = header[2];

        byte proto = (byte) (flag & SERIALIZATION_MASK);

        Serialization s = CodecSupport.getSerialization(channel.getUrl(), proto);

        long id = Bytes.bytes2long(header, 4);

        if ((flag & FLAG_REQUEST) == 0) {
            Response res = new Response(id);
            if ((flag & FLAG_EVENT) != 0) {
                res.setEvent(Response.HEARTBEAT_EVENT);
            }
            byte status = header[3];
            res.setStatus(status);
            if (status == Response.OK) {
                try {
                    Object data;
                    if (res.isHeartbeat()) {
                        data = decodeHeartbeatData(channel, deserialize(s, channel.getUrl(), is));
                    } else if (res.isEvent()) {
                        data = decodeEventData(channel, deserialize(s, channel.getUrl(), is));
                    } else {
                        DecodeableRpcResult result;
                        if (channel.getUrl().getParameter(Constants.DECODE_IN_IO_THREAD_KEY, Constants.DEFAULT_DECODE_IN_IO_THREAD)) {
                            result = new DecodeableRpcResult(channel, res, is, (Invocation) getRequestData(id), proto);
                            result.decode();
                        } else {
                            result = new DecodeableRpcResult(channel, res, new UnsafeByteArrayInputStream(readMessageData(is)), (Invocation) getRequestData(id), proto);
                        }
                        data = result;
                    }
                    res.setResult(data);
                } catch (Throwable t) {
                    if (log.isWarnEnabled()) {
                        log.warn("Decode response failed: " + t.getMessage(), t);
                    }
                    res.setStatus(Response.CLIENT_ERROR);
                    res.setErrorMessage(StringUtils.toString(t));
                }
            } else {
                res.setErrorMessage(deserialize(s, channel.getUrl(), is).readUTF());
            }
            return res;
        } else {
            Request req = new Request(id);
            req.setVersion("2.0.0");
            req.setTwoWay((flag & FLAG_TWOWAY) != 0);
            if ((flag & FLAG_EVENT) != 0) {
                req.setEvent(Request.HEARTBEAT_EVENT);
            }
            try {
                Object data;
                if (req.isHeartbeat()) {
                    data = decodeHeartbeatData(channel, deserialize(s, channel.getUrl(), is));
                } else if (req.isEvent()) {
                    data = decodeEventData(channel, deserialize(s, channel.getUrl(), is));
                } else {
                    DecodeableRpcInvocation inv;
                    if (channel.getUrl().getParameter(Constants.DECODE_IN_IO_THREAD_KEY, Constants.DEFAULT_DECODE_IN_IO_THREAD)) {
                        inv = new DecodeableRpcInvocation(channel, req, is, proto);
                        inv.decode();
                    } else {
                        inv = new DecodeableRpcInvocation(channel, req, new UnsafeByteArrayInputStream(readMessageData(is)), proto);
                    }
                    data = inv;
                }
                req.setData(data);
            } catch (Throwable t) {
                if (log.isWarnEnabled()) {
                    log.warn("Decode request failed: " + t.getMessage(), t);
                }
                req.setBroken(true);
                req.setData(t);
            }
            return req;
        }
    }

代码很长，但是这是解析一个dubbo协议包的payload部分，十分重要，应该说超级重要，我详细来介绍下。

1. 获得头部的第3个字节flag
	- 5位采用的序列化协议标识(掩码00011111)
	- 1位event标识(掩码00100000)
	- 1位TwoWay标识(掩码01000000)
	- 1位REQ/RES标识(掩码10000000)

2. 获得序列化协议标识proto

3. 获得相应的序列化协议对象s，dubbo默认是hession2，可以通过协议包中获得，也可以通过url中信息获得

4. 获得头部中的id字段，长64位8个字节，使用long来接收

5. 标志位为RES的处理（response）：产生Response的实例res
	1. 为res设置id
	
	2. 为res设置HEARTBEAT_EVENT的event，如果event标识是1的话
	
	3. 为res设置status，字段值来自协议头部第4个字节
	
	4. 根据status的处理，status要求是Response.OK
		1. 根据res的情况进行不同的处理
			1. res.isHeartbeat()
			2. res.isEvent()
			3. 其他
	5. status不是Response.OK，或者反序列化过程出了错，都会进行设置相应错误
		- 反序列过程扔出的异常处理
			- 设置res的status为90（client_error说明客户端有误）
			- 设置res的错误消息为异常内容
		- 其他
			- 设置res的错误消息为payload中的内容
7. 标志位为REQ的处理（request）：产生Request的实例req
	1. 为req设置id
	
	2. 为req设置版本2.0.0
	
	3. 为req设置TwoWay标志
	
	4. 根据req的情况进行不同的处理
			1. res.isHeartbeat()
			2. res.isEvent()
			3. 其他
	5. req设定内容

上面的逻辑介绍的不是很清楚，还有很多细节没有讲清，主要是解码过程


### 7.4.3其他的处理 ###

----------

	DecodeableRpcInvocation inv;
    if (channel.getUrl().getParameter(Constants.DECODE_IN_IO_THREAD_KEY, Constants.DEFAULT_DECODE_IN_IO_THREAD)) {
    	inv = new DecodeableRpcInvocation(channel, req, is, proto);
        inv.decode();
    } else {
    	inv = new DecodeableRpcInvocation(channel, req, new UnsafeByteArrayInputStream(readMessageData(is)), proto);
    }
    data = inv;

根据url中的方式选择合适的操作，一般都是第一个操作:
	
	inv = new DecodeableRpcInvocation(channel, req, is, proto);
    inv.decode();

构建了一个DecodeableRpcInvocation，然后进行解码操作

	public void decode() throws Exception {
        if (!hasDecoded && channel != null && inputStream != null) {
            try {
                decode(channel, inputStream);
            } catch (Throwable e) {
                if (log.isWarnEnabled()) {
                    log.warn("Decode rpc invocation failed: " + e.getMessage(), e);
                }
                request.setBroken(true);
                request.setData(e);
            } finally {
                hasDecoded = true;
            }
        }
    }

然后

	public Object decode(Channel channel, InputStream input) throws IOException {
        ObjectInput in = CodecSupport.getSerialization(channel.getUrl(), serializationType)
            .deserialize(channel.getUrl(), input);

        setAttachment(Constants.DUBBO_VERSION_KEY, in.readUTF());
        setAttachment(Constants.PATH_KEY, in.readUTF());
        setAttachment(Constants.VERSION_KEY, in.readUTF());

        setMethodName(in.readUTF());
        try {
            Object[] args;
            Class<?>[] pts;
            String desc = in.readUTF();
            if (desc.length() == 0) {
                pts = DubboCodec.EMPTY_CLASS_ARRAY;
                args = DubboCodec.EMPTY_OBJECT_ARRAY;
            } else {
                pts = ReflectUtils.desc2classArray(desc);
                args = new Object[pts.length];
                for (int i = 0; i < args.length; i++) {
                    try {
                        args[i] = in.readObject(pts[i]);
                    } catch (Exception e) {
                        if (log.isWarnEnabled()) {
                            log.warn("Decode argument failed: " + e.getMessage(), e);
                        }
                    }
                }
            }
            setParameterTypes(pts);

            Map<String, String> map = (Map<String, String>) in.readObject(Map.class);
            if (map != null && map.size() > 0) {
                Map<String, String> attachment = getAttachments();
                if (attachment == null) {
                    attachment = new HashMap<String, String>();
                }
                attachment.putAll(map);
                setAttachments(attachment);
            }
            //decode argument ,may be callback
            for (int i = 0; i < args.length; i++) {
                args[i] = decodeInvocationArgument(channel, this, pts, i, args[i]);
            }

            setArguments(args);

        } catch (ClassNotFoundException e) {
            throw new IOException(StringUtils.toString("Read invocation data failed.", e));
        }
        return this;
    }

### nettyHandler的处理
---

上面我们说明的都是解码handler的处理，当解码handler序列化好对象后，进入下一步事件，自然就是被传递给nettyHandler的处理。我们来详细的说明下

    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        NettyChannel channel = NettyChannel.getOrAddChannel(ctx.getChannel(), url, handler);
        try {
            handler.received(channel, e.getMessage());
        } finally {
            NettyChannel.removeChannelIfDisconnected(ctx.getChannel());
        }
    }
代码很简单，但是实际逻辑确实很复杂，上面我们已经说过，这个handler不知道嵌套包装了多少个handler。

1. 最外层NettyServer该类也实现了ChannelHandler方法，他使用父类的默认实现，也就是AbstractPeer的实现，做的事情也很简单，简单传递给内部ChannelHandler处理
2. MultiMessageHandler的处理
	1. 简单的处理多消息
3. HeartbeatHandler的处理
	1. 心跳处理
4. AllChannelHandler的处理
	1. 提到线程处理
5. DecodeHandler的处理
	1. 解码message的各种类型
6. HeaderExchangeHandler的处理
	1. 根据message类型处理
7. ExchangeHandlerAdapter的处理
	1. 对于message是Invocation进行处理，反之什么也不干。


### ExchangeHandlerAdapter.reply处理message
---
这里是最后一步的处理。
