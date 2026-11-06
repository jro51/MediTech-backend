import google.generativeai as genai
import os
from dotenv import load_dotenv

load_dotenv()

USE_REAL_AI = False

if USE_REAL_AI:
    genai.configure(api_key=os.getenv("GEMINI_API_KEY"))

def generate_recommendation(product_names: list[str], total: float) -> str:
    # En modo prueba, devolvemos un mensaje simulado
    if not USE_REAL_AI:
        return "MENSAJE MOCK: Toma tus medicamentos con agua y a la misma hora cada día. Mantén una dieta saludable para mejores resultados."

    try:
        products_text = ", ".join(product_names)
        prompt = f"""Eres un asistente médico especializado en farmacología.
        Un paciente acaba de comprar los siguientes medicamentos: {products_text}.
        El total de su compra fue ${total:.2f}.

        Por favor genera una recomendación breve y útil (máximo 3 oraciones) que incluya:
        1. Una advertencia importante sobre el uso correcto
        2. Un consejo de salud relevante para estos medicamentos

        Sé directo, empático y profesional. No uses formato markdown."""

        # Usamos el modelo que confirmaste que funciona
        model = genai.GenerativeModel('models/gemini-2.0-flash')
        response = model.generate_content(prompt)

        return response.text

    except Exception as e:
        print(f"Error al conectar con Gemini: {e}")
        # Fallback si la IA falla
        return "Gracias por tu compra. Por favor, lee las instrucciones del empaque y consulta a tu médico ante cualquier duda."