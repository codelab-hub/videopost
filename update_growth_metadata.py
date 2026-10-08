import os
import json
import sqlite3

video_dir = "/Users/ludmilamoreira/Desktop/frutinhas do brasil/videos curtos campanha.nosync"
tiktok_db = "/Users/ludmilamoreira/videopost/tiktok-service/tiktok_service.db"
yt_db = "/Users/ludmilamoreira/videopost/.videopost/profiles/noticiabrasil/videopost.db"

# Estratégia de hashtags para furar a bolha e atingir o público de Flávio Bolsonaro / Direita / Política Brasil
TARGET_HASHTAGS = [
    "#flaviobolsonaro",
    "#bolsonaro",
    "#direitabrasil",
    "#politica",
    "#noticiabrasil",
    "#congresso",
    "#conservador",
    "#brasil",
    "#opiniao",
    "#fatos",
    "#urgente"
]

# Mapeamento de legendas magnéticas com gatilhos de engajamento, curiosidade e debate
CUSTOM_CAPTIONS = {
    "d040d96ef6a94757b700368a8306a7b3": "O debate sobre a jornada de trabalho que está dividindo o país! A oposição alerta sobre o impacto na economia. Qual é o seu lado nessa discussão? Concorda ou discorda? Deixe sua opinião sincera nos comentários! 👇",
    "f13c81056ecc4272a587472b8d87be80": "Fatos sobre leis e direitos que você precisa saber antes de tirar conclusões! A bancada de oposição cobrou transparência em Brasília. Você concorda com essa cobrança? Assista e comente! ⚖️",
    "tuitevictor_2107464566494187637": "O que a grande mídia muitas vezes não destaca nos bastidores de Brasília. Flávio Bolsonaro e parlamentares cobraram explicações. Concorda com essa postura? Assista até o final e deixe seu posicionamento! 🗣️",
    "LulaOficial_2107611727240089615": "Declarações marcantes que continuam repercutindo em todo o Brasil. A oposição não deixou passar e rebateu as falas. Qual é a sua avaliação sobre isso? Concorda ou discorda? Comente aqui! 🎙️",
    "MayaaaRial_2107561655391707378": "Frases e declarações que revelam muito sobre o momento político do Brasil hoje! Você concorda com esse ponto de vista ou acha que a oposição está certa? Deixe sua opinião nos comentários! 💬",
    "crushdobbb20_2107699140553495016": "A história e a evolução do Brasil que mostram a força do nosso povo! Fatos e reflexões para quem realmente ama este país. Você se orgulha da nossa trajetória? Deixe seu comentário patriota! 🇧🇷",
    "leosdunphy_2107487213890289705_1": "O vídeo que está movimentando o debate político nas redes! A verdade por trás das narrativas que tentam impor. Concorda com essa reflexão? Deixe seu recado nos comentários! 📱",
    "sapplch_2107854517681390059": "Fatos e dados concretos que colocam em cheque os discursos oficiais. Flávio Bolsonaro e aliados já haviam alertado sobre isso. O que você acha? Deixe sua opinião sincera! 💡",
    "vaidesmaiar_2107692799176974482": "Cenas e bastidores que mostram a realidade sem filtros. Veja a reação e me diga nos comentários: você teria a mesma atitude? Concorda ou discorda? 👇",
    "vaidesmaiar_2107719373645345061": "Detalhes dos bastidores que deram o que falar na internet! A oposição reagiu na hora. Você acha que exageraram ou tinham toda razão? Diga nos comentários! 👀",
    "vaidesmaiar_2107820413518770182": "Situações inacreditáveis que resumem o cenário atual do Brasil. Assista até o fim e responda: isso te revolta ou é normal hoje em dia? Comente aqui! 😂",
    "vaidesmaiar_2107830009767600199": "Fatos que o brasileiro precisa ver com os próprios olhos para acreditar! O debate em Brasília esquentou com esse assunto. Qual lado você apoia? Deixe sua voz nos comentários! 🗣️",
    "vaidesmaiar_2107835427306565806": "Reflexão cirúrgica sobre o comportamento político e social no Brasil. Você se sente representado por essa visão? Concorda ou discorda? Comente agora! 💬",
    "vaidesmaiar_2107847493975175678": "Flagra direto dos bastidores que causou polêmica imediata! Quando a máscara cai diante do público. Qual é a sua opinião sobre essa cena? Comente aqui! 🎬",
    "vaidesmaiar_2107848083237126574": "O momento ao vivo que surpreendeu todo mundo e viralizou na internet! A verdade não pode ser calada. O que você achou dessa reação? Deixe seu comentário! 🎥",
    "vaidesmaiar_2107854291499446287": "Momentos marcantes que provam como a internet não esquece nada. Flávio Bolsonaro e líderes da direita comentaram essa postura. Você concorda com eles? Diga abaixo! ✨",
    "vaidesmaiar_2107863787294585300": "A reação espontânea que viralizou e representa o sentimento de milhões de brasileiros hoje! Você reagiria da mesma forma? Deixe sua opinião nos comentários! 🤩",
    "vaidesmaiar_2107865721132945676": "Cenas históricas da TV brasileira que continuam rendendo debate acalorado! A narrativa vs a realidade. De que lado da história você está? Comente aqui! 📺",
    "vaidesmaiar_2107883709277856188": "O detalhe no final que a maioria não percebeu de primeira! Veja com atenção e me diga se você notou. O que você achou disso? Assista e comente! 🔍",
    "vaidesmaiar_2107891106352414953": "O comportamento das pessoas nas redes revela a real divisão do Brasil. A direita e a esquerda em confronto direto de ideias. Em quem você confia mais? Opine nos comentários! 🤔",
    "vaidesmaiar_2107900840992543048": "Momentos hilários e inusitados que provam que o Brasil não é para amadores! Rir para não chorar diante dessa situação. Concorda com essa reação? Comente aqui! 🎭",
    "vaidesmaiar_2107901946120007907": "Assunto bombando em todas as timelines do Brasil hoje! A oposição usou esse fato para questionar o governo. Você acha que eles estão certos? Deixe seu posicionamento! 📈",
    "vaidesmaiar_2107903927140372638": "Declarações fortes que deixaram o público surpreso! Quando a verdade vem à tona sem filtros. Você apoia ou condena essa fala? Deixe sua opinião sincera! 🎙️",
    "vaidesmaiar_2107904344486207579": "O poder das redes sociais em mostrar aquilo que a mídia tradicional ignora. A voz do povo e dos patriotas tem força! Você concorda com isso? Deixe seu comentário! ⚡",
    "vaidesmaiar_2107913070638977309 (1)": "O debate que movimentou Brasília e incendiou a internet esta semana! Flávio Bolsonaro e parlamentares não pouparam críticas. Qual é a sua opinião sobre o assunto? Comente aqui! 💬",
    "vaidesmaiar_2107913070638977309": "Situações do cotidiano brasileiro comentadas com firmeza e bom humor! A realidade do trabalhador que acorda cedo. Você se identifica com isso? Deixe seu recado nos comentários! 😄",
    "vocefaloumerda_2107625442887028782": "Reflexão direta e sem mimimi sobre os valores e a convivência no Brasil de hoje! Defender princípios e liberdade incomoda muita gente. Concorda com essa postura? Comente agora! 🤔",
    "1791369200733": "Debates quentes que estão movimentando a política e o Senado em Brasília! Flávio Bolsonaro e a oposição cobram transparência. Você apoia essa cobrança? Deixe sua opinião sincera nos comentários! 💬",
    "1791370097420": "Dados e notícias urgentes que a grande mídia não mostra com clareza! A realidade da economia e do Brasil que impacta o seu bolso. Concorda com esse alerta? Assista até o fim e comente! 📊",
    "1791370872636": "Uma lição valiosa sobre coragem e decisões cruciais para o futuro do nosso país! Momentos em que ficar em cima do muro não é opção. De que lado você fica? Deixe seu posicionamento nos comentários! 💡",
    "961adad8c2464b72bf624791bcba0bef": "Como a tecnologia e as redes sociais deram voz ao povo para furar a bolha das grandes emissoras! Liberdade de expressão em primeiro lugar. Você defende essa liberdade? Comente abaixo! 📱"
}

# 1. Atualiza arquivos JSON na pasta de vídeos
print("Atualizando arquivos .json na pasta de vídeos...")
count_json = 0
for stem, caption in CUSTOM_CAPTIONS.items():
    json_path = os.path.join(video_dir, f"{stem}.json")
    data = {
        "caption": caption,
        "hashtags": TARGET_HASHTAGS
    }
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    count_json += 1

print(f"✅ {count_json} arquivos .json atualizados com sucesso!")

# 2. Atualiza banco SQLite do TikTok (tiktok_service.db)
if os.path.exists(tiktok_db):
    print("Atualizando banco de dados do TikTok (tiktok_service.db)...")
    conn = sqlite3.connect(tiktok_db)
    for stem, caption in CUSTOM_CAPTIONS.items():
        fname_mp4 = f"{stem}.mp4"
        fname_mov = f"{stem}.mov"
        tags_json = json.dumps(TARGET_HASHTAGS, ensure_ascii=False)
        conn.execute("""
            UPDATE queue
            SET caption = ?, hashtags = ?
            WHERE filename = ? OR filename = ?
        """, (caption, tags_json, fname_mp4, fname_mov))
    conn.commit()
    conn.close()
    print("✅ Banco do TikTok atualizado!")

# 3. Atualiza banco SQLite do YouTube Shorts (videopost.db)
if os.path.exists(yt_db):
    print("Atualizando banco de dados do YouTube Shorts (videopost.db)...")
    conn = sqlite3.connect(yt_db)
    for stem, caption in CUSTOM_CAPTIONS.items():
        fname_mp4 = f"{stem}.mp4"
        fname_mov = f"{stem}.mov"
        tags_str = " ".join(TARGET_HASHTAGS)
        conn.execute("""
            UPDATE videos
            SET caption = ?, hashtags = ?
            WHERE (filename = ? OR filename = ?) AND status = 'SCHEDULED'
        """, (caption, tags_str, fname_mp4, fname_mov))
    conn.commit()
    conn.close()
    print("✅ Banco do YouTube Shorts atualizado!")
