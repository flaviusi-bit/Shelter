export type Animal={id:string;name:string;animalType:string;sex:string;dateOfBirth?:string;weightKg?:number;microchipNumber?:string;intakeDate:string;rescueSource?:string;location?:string;status:string;notes?:string}
export type Treatment={id:string;medication:string;dose:string;route:string;frequency:string;startDate:string;endDate?:string;status:string;instructions?:string;prescribedBy?:string}
async function json(r:Response){if(!r.ok)throw new Error(await r.text());return r.json()}
export async function getAnimals(q=''):Promise<Animal[]>{return json(await fetch('/api/animals'+(q?'?q='+encodeURIComponent(q):'')))}
export async function createAnimal(a:Omit<Animal,'id'>):Promise<Animal>{return json(await fetch('/api/animals',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(a)}))}
export async function getTreatments(animalId:string):Promise<Treatment[]>{return json(await fetch('/api/animals/'+animalId+'/treatments'))}
