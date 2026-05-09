document.addEventListener('DOMContentLoaded', () => {
    const tabBtns = document.querySelectorAll('.tab-btn');
    const tabContents = document.querySelectorAll('.tab-content');
    const uploadZone = document.getElementById('uploadZone');
    const fileInput = document.getElementById('fileInput');
    const uploadProgress = document.getElementById('uploadProgress');
    const progressFill = document.getElementById('progressFill');
    const uploadStatus = document.getElementById('uploadStatus');
    const documentList = document.getElementById('documentList');
    const chatHistory = document.getElementById('chatHistory');
    const questionInput = document.getElementById('questionInput');
    const sendBtn = document.getElementById('sendBtn');

    // Tab switching
    tabBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            tabBtns.forEach(b => b.classList.remove('active'));
            tabContents.forEach(c => c.classList.remove('active'));
            btn.classList.add('active');
            document.getElementById('tab-' + btn.dataset.tab).classList.add('active');
        });
    });

    // File upload via click
    uploadZone.addEventListener('click', () => fileInput.click());

    fileInput.addEventListener('change', (e) => {
        if (e.target.files.length > 0) {
            uploadFile(e.target.files[0]);
        }
    });

    // Drag and drop
    uploadZone.addEventListener('dragover', (e) => {
        e.preventDefault();
        uploadZone.classList.add('drag-over');
    });

    uploadZone.addEventListener('dragleave', () => {
        uploadZone.classList.remove('drag-over');
    });

    uploadZone.addEventListener('drop', (e) => {
        e.preventDefault();
        uploadZone.classList.remove('drag-over');
        if (e.dataTransfer.files.length > 0) {
            uploadFile(e.dataTransfer.files[0]);
        }
    });

    // Upload file
    async function uploadFile(file) {
        const formData = new FormData();
        formData.append('file', file);

        uploadProgress.style.display = 'block';
        progressFill.style.width = '0%';
        uploadStatus.textContent = '正在上传 ' + file.name + '...';

        try {
            const response = await fetch('/api/documents/upload', {
                method: 'POST',
                body: formData
            });

            if (!response.ok) {
                const err = await response.json();
                throw new Error(err.error || '上传失败');
            }

            const result = await response.json();
            progressFill.style.width = '100%';
            uploadStatus.textContent = '上传成功！文档已分为 ' + result.chunkCount + ' 个分块';

            setTimeout(() => {
                uploadProgress.style.display = 'none';
            }, 3000);

            loadDocuments();
        } catch (err) {
            uploadStatus.textContent = '上传失败：' + err.message;
            progressFill.style.width = '0%';
        }

        fileInput.value = '';
    }

    // Load documents
    async function loadDocuments() {
        try {
            const response = await fetch('/api/documents');
            const docs = await response.json();

            if (docs.length === 0) {
                documentList.innerHTML = '<p class="empty-hint">暂无文档</p>';
                return;
            }

            documentList.innerHTML = docs.map(doc => `
                <div class="doc-item">
                    <div class="doc-info">
                        <span class="doc-filename">${escapeHtml(doc.filename)}</span>
                        <div class="doc-meta">${doc.uploadTime}</div>
                    </div>
                    <span class="doc-chunks">${doc.chunkCount} 块</span>
                    <button class="delete-btn" onclick="deleteDocument('${doc.docId}')">删除</button>
                </div>
            `).join('');
        } catch (err) {
            documentList.innerHTML = '<p class="empty-hint">加载文档列表失败</p>';
        }
    }

    // Delete document
    window.deleteDocument = async function(docId) {
        if (!confirm('确定要删除此文档吗？')) return;

        try {
            await fetch('/api/documents/' + docId, { method: 'DELETE' });
            loadDocuments();
        } catch (err) {
            alert('删除失败：' + err.message);
        }
    };

    // Chat
    sendBtn.addEventListener('click', sendQuestion);
    questionInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendQuestion();
        }
    });

    async function sendQuestion() {
        const question = questionInput.value.trim();
        if (!question) return;

        // Remove welcome message
        const welcome = chatHistory.querySelector('.chat-welcome');
        if (welcome) welcome.remove();

        // Show question
        appendMessage('question', question);
        questionInput.value = '';
        sendBtn.disabled = true;

        // Show loading
        const loadingId = 'loading-' + Date.now();
        const loadingDiv = document.createElement('div');
        loadingDiv.id = loadingId;
        loadingDiv.className = 'chat-message';
        loadingDiv.innerHTML = '<div class="message-answer"><span class="loading"></span>正在思考...</div>';
        chatHistory.appendChild(loadingDiv);
        chatHistory.scrollTop = chatHistory.scrollHeight;

        try {
            const response = await fetch('/api/chat', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ question: question })
            });

            const result = await response.json();

            // Remove loading
            document.getElementById(loadingId).remove();

            // Show answer
            const answerDiv = document.createElement('div');
            answerDiv.className = 'chat-message';
            let html = '<div class="message-label">回答：</div>';
            html += '<div class="message-answer">' + escapeHtml(result.answer) + '</div>';

            if (result.sources && result.sources.length > 0) {
                html += '<div class="message-sources">来源：';
                result.sources.forEach(s => {
                    html += '<span class="source-tag">' + escapeHtml(s.filename)
                        + ' (' + (s.similarity * 100).toFixed(1) + '%)</span>';
                });
                html += '</div>';
            }

            answerDiv.innerHTML = html;
            chatHistory.appendChild(answerDiv);
        } catch (err) {
            document.getElementById(loadingId).remove();
            appendMessage('answer', '请求失败：' + err.message);
        }

        sendBtn.disabled = false;
        chatHistory.scrollTop = chatHistory.scrollHeight;
    }

    function appendMessage(type, content) {
        const div = document.createElement('div');
        div.className = 'chat-message';
        if (type === 'question') {
            div.innerHTML = '<div class="message-label">问题：</div>'
                + '<div class="message-question">' + escapeHtml(content) + '</div>';
        } else {
            div.innerHTML = '<div class="message-answer">' + escapeHtml(content) + '</div>';
        }
        chatHistory.appendChild(div);
        chatHistory.scrollTop = chatHistory.scrollHeight;
    }

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // Initial load
    loadDocuments();
});
